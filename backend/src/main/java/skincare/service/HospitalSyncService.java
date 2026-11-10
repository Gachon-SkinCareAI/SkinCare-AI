package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;
import skincare.dto.HospitalSyncRequestDto;
import skincare.dto.HospitalSyncResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.DirectoryException;
import skincare.external.HiraHospitalDirectory;
import skincare.external.HiraHospitalDirectory.Facility;
import skincare.external.NmcDirectory;
import skincare.external.NmcDirectory.Place;
import skincare.repository.HospitalRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// 공공데이터 API 기반 의료기관/약국 DB 동기화 비즈니스 로직 처리
//  - 병원: 건강보험심사평가원 병원정보서비스(피부과 진료과목을 가진 모든 기관) + 국립중앙의료원 병·의원 정보(진료시간·응급실)
//  - 약국: 국립중앙의료원 "전국 약국 정보 조회 서비스"
// 처리 흐름: 공공데이터 수집 → 기존 DB 정보와 비교 → 신규 기관 추가 / 변경 기관 수정 / 제외된 기관 운영 중지
@Slf4j
@Service
@RequiredArgsConstructor
// HospitalSyncService 비즈니스 로직 처리
public class HospitalSyncService {

    private static final int BATCH_SIZE = 1000;
    // 기존에 운영 중이던 기관 수가 이 이상일 때, 새로 수집한 수가 이 비율 미만이면 부분 수집으로 보고 중단
    private static final int DROP_GUARD_MIN_EXISTING = 100;
    private static final double DROP_GUARD_RATIO = 0.8;

    // 병원정보와 국립중앙의료원 정보의 결합률이 이 미만이면 응답 형식이 바뀌었거나 데이터가 이상한 것으로 보고 중단
    private static final int MATCH_GUARD_MIN_FACILITIES = 100;
    private static final double MATCH_GUARD_RATIO = 0.8;

    // 지역 지정 동기화용
    private static final Map<String, List<String>> REGION_NAMES =
            Map.ofEntries(
                    Map.entry("110000", List.of("서울특별시")),
                    Map.entry("210000", List.of("부산광역시")),
                    Map.entry("220000", List.of("인천광역시")),
                    Map.entry("230000", List.of("대구광역시")),
                    Map.entry("250000", List.of("대전광역시")),
                    Map.entry("260000", List.of("울산광역시")),
                    Map.entry("310000", List.of("경기도")),
                    Map.entry("320000", List.of("강원특별자치도")),
                    Map.entry("330000", List.of("충청북도")),
                    Map.entry("340000", List.of("충청남도")),
                    Map.entry("350000", List.of("전북특별자치도")),
                    Map.entry("360000", List.of("전남광주통합특별시", "전라남도", "광주광역시")),
                    Map.entry("370000", List.of("경상북도")),
                    Map.entry("380000", List.of("경상남도")),
                    Map.entry("390000", List.of("제주특별자치도")),
                    Map.entry("410000", List.of("세종특별자치시")));

    private final HospitalRepository hospitalRepository; // 의료기관 데이터 베이스 접근용 Repository
    private final NmcDirectory nmcDirectory; // 국립중앙의료원 약국·병의원 목록 수집기
    private final HiraHospitalDirectory hiraDirectory; // 건강보험심사평가원 병원정보(피부과) 수집기
    private final jakarta.persistence.EntityManager entityManager; // 신규 기관을 persist로 저장하기 위함
    private final PlatformTransactionManager transactionManager;

    // 병원/약국 공공데이터 동기화 실행
    // 처리 중 오류가 발생하면 전체 변경을 롤백하고 HOSPITAL-004 반환
    public HospitalSyncResponseDto syncHospitals(HospitalSyncRequestDto dto) {
        long startTime = System.currentTimeMillis();
        String syncId =
                "SYNC-"
                        + LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String scope =
                StringUtils.hasText(dto.getTargetScope())
                        ? dto.getTargetScope().trim().toUpperCase(Locale.ROOT)
                        : "ALL";
        boolean syncHospitals =
                scope.equals("ALL") || scope.equals("HOSPITAL") || scope.equals("DERMATOLOGY");
        boolean syncPharmacies = scope.equals("ALL") || scope.equals("PHARMACY");
        if (!syncHospitals && !syncPharmacies) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "target_scope는 ALL, HOSPITAL, PHARMACY 중 하나여야 합니다.");
        }
        String regionCode = StringUtils.hasText(dto.getRegionCode()) ? dto.getRegionCode().trim() : null;
        List<String> regionNames = null; // null이면 전국
        if (regionCode != null) {
            regionNames = REGION_NAMES.get(regionCode);
            if (regionNames == null) {
                throw new CustomException(
                        ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 시도코드입니다: " + regionCode);
            }
        }

        try {

            // 1. 수집 (DB 트랜잭션 밖)
            // 서로 다른 API라 동시에 수집
            // 하나라도 실패하면 중단
            List<Hospital> fetched = collect(syncHospitals, syncPharmacies, regionCode, regionNames);

            // 아무것도 수집하지 못한 경우 기존 데이터 전체가 운영 중지되지 않도록 중단
            if (fetched.isEmpty()) {
                throw new CustomException(
                        ErrorCode.HOSPITAL_SYNC_FAILED, "공공데이터에서 기관 정보를 읽지 못했습니다.");
            }

            // 2. 비교·저장 (하나의 트랜잭션)
            return new TransactionTemplate(transactionManager)
                    .execute(
                            status ->
                                    apply(
                                            fetched,
                                            regionCode == null,
                                            scope,
                                            syncHospitals,
                                            syncPharmacies,
                                            syncId,
                                            startTime));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Sync] 서버 오류, 의료기관 동기화 처리 중 에러 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.HOSPITAL_SYNC_FAILED);
        }
    }

    // 세 API(건강보험심사평가원 병원, 국립중앙의료원 병·의원, 국립중앙의료원 약국)를 동시에 수집해 동기화용 엔티티로 변환
    private List<Hospital> collect(
            boolean syncHospitals, boolean syncPharmacies, String regionCode, List<String> regionNames) {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<List<Facility>> hira =
                    syncHospitals ? pool.submit(() -> hiraDirectory.fetchDermatology(regionCode)) : null;
            Future<List<Place>> nmcHospitals =
                    syncHospitals ? pool.submit(() -> fetchNmc(true, regionNames)) : null;
            Future<List<Place>> nmcPharmacies =
                    syncPharmacies ? pool.submit(() -> fetchNmc(false, regionNames)) : null;

            Map<String, Hospital> byId = new LinkedHashMap<>(); // 같은 ID가 여러 곳에 있으면 하나만 쓴다
            LocalDate syncDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
            if (syncHospitals) {
                hospitalsFrom(await(hira), await(nmcHospitals), syncDate)
                        .forEach(h -> byId.putIfAbsent(h.getHospitalId(), h));
            }
            if (syncPharmacies) {
                pharmaciesFrom(await(nmcPharmacies), syncDate)
                        .forEach(h -> byId.putIfAbsent(h.getHospitalId(), h));
            }
            return new ArrayList<>(byId.values());
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Place> fetchNmc(boolean hospitals, List<String> regionNames) {
        log.info("[Sync] 국립중앙의료원 {} 수집 시작 ({})...", hospitals ? "병·의원" : "약국", regionNames == null ? "전국" : regionNames);
        if (regionNames == null)
            return hospitals ? nmcDirectory.fetchHospitals(null) : nmcDirectory.fetchPharmacies(null);
        Map<String, Place> byId = new LinkedHashMap<>();
        for (String region : regionNames)
            for (Place p : hospitals ? nmcDirectory.fetchHospitals(region) : nmcDirectory.fetchPharmacies(region))
                byId.putIfAbsent(p.hpid(), p);
        return new ArrayList<>(byId.values());
    }


    // 수집 작업의 결과 대기
    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DirectoryException failure)
                throw new CustomException(ErrorCode.HOSPITAL_SYNC_FAILED, failureMessage(failure));
            if (e.getCause() instanceof CustomException known) throw known;
            throw new CustomException(ErrorCode.HOSPITAL_SYNC_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.HOSPITAL_SYNC_FAILED);
        }
    }


    // 피부과 진료과목을 가진 기관에 국립중앙의료원의 진료시간·응급실 정보를 결합한다
    private List<Hospital> hospitalsFrom(List<Facility> facilities, List<Place> places, LocalDate syncDate) {
        var match = HospitalMatcher.match(facilities, places);
        log.info(
                "[Sync] 병원 {}곳 결합: 일치 {} (이름+거리 {}, 근접·포함 {}, 시군구 {}), 불일치 {}, 여러 기관이 같은 국립중앙의료원 항목을 가리킨 경우 {}",
                facilities.size(),
                match.matched(),
                match.byName(),
                match.byProximity(),
                match.bySigungu(),
                match.unmatched(),
                match.sharedPlaces());
        // 결합률이 갑자기 낮아지면 형식 변경·부분 응답이므로 진료시간·응급실이 대량으로 비워지기 전에 중단한다
        if (facilities.size() >= MATCH_GUARD_MIN_FACILITIES && match.rate() < MATCH_GUARD_RATIO) {
            throw new CustomException(
                    ErrorCode.HOSPITAL_SYNC_FAILED,
                    "병원 정보와 국립중앙의료원 진료시간 정보의 결합률("
                            + Math.round(match.rate() * 100)
                            + "%)이 너무 낮아 동기화를 중단했습니다. 기존 데이터는 변경되지 않았습니다.");
        }
        List<Hospital> result = new ArrayList<>(facilities.size());
        for (Facility f : facilities) result.add(toHospital(f, match.byYkiho().get(f.ykiho()), syncDate));
        return result;
    }

    private List<Hospital> pharmaciesFrom(List<Place> places, LocalDate syncDate) {
        List<Hospital> result = new ArrayList<>(places.size());
        for (Place p : places) {
            String id = Hospital.NMC_ID_PREFIX + p.hpid();
            if (id.length() > 50) continue; // hospital_id 컬럼(50자)에 들어가지 않는 식별자 건너뜀
            result.add(toPharmacy(p, id, syncDate));
        }
        log.info("[Sync] 약국 {}건 수집 완료", result.size());
        return result;
    }


    // 수집한 기관을 기존 DB와 비교해 추가·수정·중지
    private HospitalSyncResponseDto apply(
            List<Hospital> fetched,
            boolean wholeCountry,
            String scope,
            boolean syncHospitals,
            boolean syncPharmacies,
            String syncId,
            long startTime) {
        // 기존 DB 정보와 비교하여 신규 추가 / 변경 수정
        Map<String, Hospital> existingMap = managedHospitals();

        if (wholeCountry) {
            guardAgainstPartialFetch(existingMap, fetched, syncPharmacies, true, "약국");
            guardAgainstPartialFetch(existingMap, fetched, syncHospitals, false, "병원");
        }

        List<Hospital> newHospitals = new ArrayList<>();
        Set<String> fetchedIds = new HashSet<>();
        int updatedCount = 0;

        for (Hospital source : fetched) {
            fetchedIds.add(source.getHospitalId());
            Hospital existing = existingMap.get(source.getHospitalId());
            if (existing == null) {
                newHospitals.add(source);
            } else if (existing.updateFrom(source)) {
                updatedCount++; // 변경 감지(Dirty Checking)로 트랜잭션 종료 시 UPDATE
            }
        }

        // 이번 공공데이터에서 제외된 기관 운영 중지 처리
        int deactivatedCount = 0;
        if (wholeCountry) {
            for (Hospital existing : existingMap.values()) {
                boolean inScope =
                        existing.getHospitalType() == HospitalType.PHARMACY
                                ? syncPharmacies
                                : syncHospitals;
                if (inScope
                        && Hospital.STATUS_ACTIVE.equals(existing.getStatus())
                        && !fetchedIds.contains(existing.getHospitalId())) {
                    existing.deactivate();
                    deactivatedCount++;
                }
            }
        }

        // 신규 기관 Batch Insert (1,000건씩 분할 저장)
        log.info(
                "[Sync] 대량 DB 저장 시작 (신규 {}건, 수정 {}건, 운영중지 {}건)...",
                newHospitals.size(),
                updatedCount,
                deactivatedCount);

        for (int i = 0; i < newHospitals.size(); i += BATCH_SIZE) {
            for (Hospital created :
                    newHospitals.subList(i, Math.min(i + BATCH_SIZE, newHospitals.size()))) {
                entityManager.persist(created);
            }
            entityManager.flush(); // 배치 플러시
            entityManager.clear(); // 영속성 컨텍스트가 수만 건으로 커지지 않도록 비운다
        }

        double durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        log.info(
                "[Sync] 정상 처리, 공공데이터 의료기관 동기화 완료 (fetched: {}, added: {}, updated: {},"
                        + " deactivated: {}, {}초)",
                fetched.size(),
                newHospitals.size(),
                updatedCount,
                deactivatedCount,
                durationSeconds);

        return HospitalSyncResponseDto.builder()
                .syncId(syncId)
                .targetScope(scope)
                .executedAt(
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .durationSeconds(durationSeconds)
                .metrics(
                        HospitalSyncResponseDto.SyncMetrics.builder()
                                .totalFetched(fetched.size())
                                .newAdded(newHospitals.size())
                                .updated(updatedCount)
                                .deactivated(deactivatedCount)
                                .build())
                .status("COMPLETED")
                .build();
    }

    private Map<String, Hospital> managedHospitals() {
        return hospitalRepository.findAll().stream()
                .filter(h -> Hospital.isSyncManaged(h.getHospitalId()))
                .collect(Collectors.toMap(Hospital::getHospitalId, Function.identity()));
    }

    // 범위(약국/병원)별로 수집량이 기존 운영 기관 수의 80% 미만으로 줄었으면 중단 (수정·중지 전에 호출)
    private void guardAgainstPartialFetch(
            Map<String, Hospital> existingMap,
            List<Hospital> fetched,
            boolean inScope,
            boolean pharmacy,
            String label) {
        if (!inScope) return;
        long existing =
                existingMap.values().stream()
                        .filter(h -> (h.getHospitalType() == HospitalType.PHARMACY) == pharmacy)
                        .filter(h -> Hospital.STATUS_ACTIVE.equals(h.getStatus()))
                        .count();
        long now =
                fetched.stream()
                        .filter(h -> (h.getHospitalType() == HospitalType.PHARMACY) == pharmacy)
                        .count();
        if (existing >= DROP_GUARD_MIN_EXISTING && now < existing * DROP_GUARD_RATIO) {
            throw new CustomException(
                    ErrorCode.HOSPITAL_SYNC_FAILED,
                    "수집한 "
                            + label
                            + " 수("
                            + now
                            + ")가 운영 중인 "
                            + label
                            + " 수("
                            + existing
                            + ")보다 크게 줄어 동기화를 중단했습니다. 기존 데이터는 변경되지 않았습니다.");
        }
    }

    /**
     * 병원 한 곳을 만든다. place는 국립중앙의료원에서 결합된 기관이며 없을 수 있다(약 4%). 결합하지 못하면 진료시간은 비우고
     * 야간진료·응급실은 false로 둔다(정보 없음).
     */
    static Hospital toHospital(Facility f, Place place, LocalDate syncDate) {
        boolean matched = place != null;
        return Hospital.builder()
                .hospitalId(syncHospitalId(f.ykiho()))
                .name(truncate(f.name(), 100))
                .hospitalType(determineHospitalType(f.className(), f.name()))
                .region(truncate(regionOf(f), 50))
                .address(truncate(f.address(), 200))
                .tel(truncate(f.tel(), 20))
                .longitude(f.longitude())
                .latitude(f.latitude())
                .weekdayHours(matched ? place.weekdayHours() : null)
                .saturdayHours(matched ? place.saturdayHours() : null)
                .sundayHolidayHours(matched ? place.sundayHolidayHours() : null)
                .nightCare(matched && place.nightCare())
                .emergency(matched && Boolean.TRUE.equals(place.emergency()))
                .dataSource(matched ? HiraHospitalDirectory.SOURCE_NAME : HiraHospitalDirectory.SOURCE_NAME_HIRA_ONLY)
                // 사용자 검색에는 출처 URL과 확인일이 있는 기관만 노출된다
                .sourceUrl(HiraHospitalDirectory.SOURCE_URL)
                .verifiedOn(syncDate)
                .status(Hospital.STATUS_ACTIVE)
                .build();
    }

    static Hospital toPharmacy(Place p, String id, LocalDate syncDate) {
        return Hospital.builder()
                .hospitalId(id)
                .name(truncate(p.name(), 100))
                .hospitalType(HospitalType.PHARMACY)
                .region(truncate(regionOf(p.address()), 50))
                .address(truncate(p.address(), 200))
                .tel(truncate(p.tel(), 20))
                .longitude(p.longitude())
                .latitude(p.latitude())
                .weekdayHours(p.weekdayHours())
                .saturdayHours(p.saturdayHours())
                .sundayHolidayHours(p.sundayHolidayHours())
                .nightCare(p.nightCare())
                .emergency(false)
                .dataSource(NmcDirectory.PHARMACY_SOURCE_NAME)
                .sourceUrl(NmcDirectory.PHARMACY_SOURCE_URL)
                .verifiedOn(syncDate)
                .status(Hospital.STATUS_ACTIVE)
                .build();
    }

    // 종별 코드명 및 기관명을 기준으로 의료기관 유형(HospitalType) 결정. 모두 피부과 진료과목을 가진 기관이다.
    static HospitalType determineHospitalType(String className, String name) {
        if (className.contains("종합병원") || className.contains("상급종합")) return HospitalType.GENERAL_HOSPITAL;
        if (name.contains("피부")) return HospitalType.DERMATOLOGY;
        return HospitalType.OTHER;
    }

    // 건강보험심사평가원 시도명과 시군구명을 지역으로 쓴다 (ex. "서울 강남구")
    static String regionOf(Facility f) {
        String region = (f.sido() + " " + f.sigungu()).strip();
        return region.isEmpty() ? "기타" : region;
    }

    // 약국은 주소의 앞 두 단어(시도 + 시군구)를 지역으로 쓴다 (ex. "경기 평택시 통복시장로 19" → "경기 평택시")
    static String regionOf(String address) {
        String[] tokens = address == null ? new String[0] : address.trim().split("\\s+");
        String region =
                tokens.length >= 2 ? tokens[0] + " " + tokens[1] : tokens.length == 1 ? tokens[0] : "";
        return region.isBlank() ? "기타" : region;
    }

    /**
     * 요양기관 기호로 동기화 기관 ID를 만든다. 공공데이터의 암호화 요양기관 기호는 80자라 hospital_id(50자)에 그대로 넣을 수
     * 없으므로 SHA-1 해시(40자)를 쓴다. 같은 기호는 항상 같은 ID가 되어 반복 동기화에서 같은 기관으로 인식된다.
     */
    static String syncHospitalId(String ykiho) {
        try {
            byte[] digest =
                    java.security.MessageDigest.getInstance("SHA-1")
                            .digest(ykiho.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Hospital.SYNC_ID_PREFIX + HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-1은 모든 JVM이 제공한다
        }
    }

    // 컬럼 길이 제한에 맞게 문자열 자르기
    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // 수집 실패 원인을 운영자가 조치할 수 있는 문구로 변환 (키·URL·응답 본문은 포함하지 않는다)
    private static String failureMessage(DirectoryException e) {
        String api = e.source().label();
        return switch (e.failure()) {
            case UNCONFIGURED -> "공공데이터 인증키(PUBLIC_MEDICAL_SERVICE_KEY)가 설정되지 않아 동기화할 수 없습니다.";
            case AUTH_ERROR ->
                    api + " API 인증키가 거부되었습니다. 키가 올바른지, '" + e.source().service() + "' 활용신청이 승인되었는지 확인해 주세요.";
            case RATE_LIMITED -> "공공데이터 API 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요. (" + api + ")";
            case UNAVAILABLE -> api + " API에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.";
            case INVALID_RESPONSE -> api + " API 응답을 해석하지 못했습니다.";
            case INCOMPLETE -> api + " 목록을 모두 받지 못해 동기화를 중단했습니다. 기존 데이터는 변경되지 않았습니다.";
        };
    }
}
