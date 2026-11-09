package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;
import skincare.dto.HospitalSyncRequestDto;
import skincare.dto.HospitalSyncResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.MedicalApiClient;
import skincare.repository.HospitalRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// 공공데이터(CSV 및 응급의료기관 OpenAPI) 기반 의료기관/약국 DB 동기화 비즈니스 로직 처리
// 처리 흐름: 공공데이터 수집 → 기존 DB 정보와 비교 → 신규 기관 추가 / 변경 기관 수정 / 제외된 기관 운영 중지
@Slf4j
@Service
@RequiredArgsConstructor
// HospitalSyncService 비즈니스 로직 처리
public class HospitalSyncService {

    private static final String HOSPITAL_FILE = "/static/hospitals.csv";
    private static final String PHARMACY_FILE = "/static/pharmacies.csv";
    private static final String DETAIL_FILE = "/static/hospital_details.csv";
    private static final int BATCH_SIZE = 1000;

    private final HospitalRepository hospitalRepository; // 의료기관 데이터 베이스 접근용 Repository
    private final MedicalApiClient medicalApiClient; // 국립중앙의료원 응급의료기관 OpenAPI 연동 클라이언트

    // 병원/약국 공공데이터 동기화 실행
    // 처리 중 오류가 발생하면 전체 변경을 롤백하고 HOSPITAL-004 반환 (일부만 반영된 상태로 남지 않도록 함)
    @Transactional
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
        boolean syncHospitalFile =
                scope.equals("ALL") || scope.equals("HOSPITAL") || scope.equals("DERMATOLOGY");
        boolean syncPharmacyFile = scope.equals("ALL") || scope.equals("PHARMACY");
        if (!syncHospitalFile && !syncPharmacyFile) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "target_scope는 ALL, HOSPITAL, PHARMACY 중 하나여야 합니다.");
        }

        try {
            // 1. 국립중앙의료원 OpenAPI 응급실 정보 수집 (실패해도 CSV 기준으로 계속 진행)
            log.info("[Sync] 국립중앙의료원 응급실 정보 수집 시작...");
            Set<String> apiEmergencyHospitalNames = medicalApiClient.fetchEmergencyHospitalNames();

            // 2. 세부정보 CSV (진료시간 및 야간응급 여부) 맵 구성
            log.info("[Sync] hospital_details.csv (세부정보) 읽기 시작...");
            Map<String, HospitalDetailInfo> detailMap = loadHospitalDetails();

            // 3. 공공데이터 CSV 파싱 (대상 스코프 / 지역 필터 적용)
            List<Hospital> fetched = new ArrayList<>();
            if (syncHospitalFile) {
                fetched.addAll(
                        parseCsvFile(
                                HOSPITAL_FILE,
                                false,
                                apiEmergencyHospitalNames,
                                detailMap,
                                dto.getRegionCode()));
            }
            if (syncPharmacyFile) {
                fetched.addAll(
                        parseCsvFile(
                                PHARMACY_FILE,
                                true,
                                apiEmergencyHospitalNames,
                                detailMap,
                                dto.getRegionCode()));
            }

            // CSV를 하나도 읽지 못한 경우 기존 데이터 전체가 운영 중지되지 않도록 중단
            if (fetched.isEmpty()) {
                throw new CustomException(
                        ErrorCode.HOSPITAL_SYNC_FAILED, "공공데이터 파일에서 기관 정보를 읽지 못했습니다.");
            }

            // 4. 기존 DB 정보와 비교하여 신규 추가 / 변경 수정
            Map<String, Hospital> existingMap =
                    hospitalRepository.findAll().stream()
                            .collect(
                                    Collectors.toMap(Hospital::getHospitalId, Function.identity()));

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

            // 5. 이번 공공데이터에서 제외된 기관 운영 중지 처리 (지역 지정 동기화는 범위가 일부이므로 생략)
            int deactivatedCount = 0;
            if (!StringUtils.hasText(dto.getRegionCode())) {
                for (Hospital existing : existingMap.values()) {
                    boolean inScope =
                            existing.getHospitalType() == HospitalType.PHARMACY
                                    ? syncPharmacyFile
                                    : syncHospitalFile;
                    if (inScope
                            && Hospital.STATUS_ACTIVE.equals(existing.getStatus())
                            && !fetchedIds.contains(existing.getHospitalId())) {
                        existing.deactivate();
                        deactivatedCount++;
                    }
                }
            }

            // 6. 신규 기관 Batch Insert (1,000건씩 분할 저장)
            log.info(
                    "[Sync] 대량 DB 저장 시작 (신규 {}건, 수정 {}건, 운영중지 {}건)...",
                    newHospitals.size(),
                    updatedCount,
                    deactivatedCount);
            for (int i = 0; i < newHospitals.size(); i += BATCH_SIZE) {
                hospitalRepository.saveAll(
                        newHospitals.subList(i, Math.min(i + BATCH_SIZE, newHospitals.size())));
                hospitalRepository.flush(); // 배치 플러시
            }

            double durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
            log.info(
                    "[Sync] 정상 처리, 공공데이터 OpenAPI 의료기관 동기화 완료 (fetched: {}, added: {}, updated: {},"
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
                            LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
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

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Sync] 서버 오류, 의료기관 동기화 처리 중 에러 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.HOSPITAL_SYNC_FAILED);
        }
    }

    // 세부정보 CSV 파싱 (요양기관 기호별 진료시간, 야간진료/응급실 여부)
    private Map<String, HospitalDetailInfo> loadHospitalDetails() {
        Map<String, HospitalDetailInfo> map = new HashMap<>();
        ClassPathResource resource = new ClassPathResource(DETAIL_FILE);

        if (!resource.exists()) {
            log.warn("[Sync] 세부정보 파일이 없어 진료시간/야간진료 정보 없이 동기화합니다: {}", DETAIL_FILE);
            return map;
        }

        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(resource.getInputStream(), "EUC-KR"))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // 세부정보 CSV는 쉼표(,) 구분 파일이므로 따옴표를 고려하여 분리
                String[] tokens = splitCsvLine(line);
                if (tokens.length < 14) continue;

                String ykiho = cleanToken(tokens[0]);
                String nightErmnyYn = cleanToken(tokens[13]);

                // 평일(월~금) 진료종료시간 중 19시 이후가 하나라도 있으면 야간진료로 판단
                boolean isNightCare = false;
                int[] closeTimeIndices = {23, 25, 27, 29, 31};
                for (int idx : closeTimeIndices) {
                    if (tokens.length > idx) {
                        int closeHour = parseHour(cleanToken(tokens[idx]));
                        if (closeHour >= 19) {
                            isNightCare = true;
                            break;
                        }
                    }
                }

                // 진료시간 (평일: 월~금 중 가장 이른 시작 ~ 가장 늦은 종료, 토요일, 일요일)
                String weekdayHours =
                        formatHours(
                                tokens,
                                new int[] {22, 24, 26, 28, 30},
                                new int[] {23, 25, 27, 29, 31});
                String saturdayHours = formatHours(tokens, new int[] {32}, new int[] {33});
                String sundayHours = formatHours(tokens, new int[] {20}, new int[] {21});

                boolean isEmergency = "Y".equalsIgnoreCase(nightErmnyYn);
                map.put(
                        ykiho,
                        new HospitalDetailInfo(
                                isNightCare,
                                isEmergency,
                                weekdayHours,
                                saturdayHours,
                                sundayHours));
            }
        } catch (Exception e) {
            log.error("[Sync] hospital_details.csv 읽기 실패: {}", e.getMessage());
        }
        return map;
    }

    // 병원/약국 CSV 파일 파싱 후 동기화 비교용 엔티티 목록 생성 (DB 조회를 반복문 안에서 하지 않음)
    private List<Hospital> parseCsvFile(
            String fileName,
            boolean isPharmacyFile,
            Set<String> apiEmergencyNames,
            Map<String, HospitalDetailInfo> detailMap,
            String regionCode) {
        List<Hospital> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(fileName);
        if (!resource.exists()) {
            log.error("[Sync] 파일이 자원에 없습니다: {}", fileName);
            return result;
        }

        // 좌표 컬럼 위치 (병원: 28/29, 약국: 13/14 - 공공데이터 CSV 헤더 기준)
        int longitudeIndex = isPharmacyFile ? 13 : 28;
        int latitudeIndex = isPharmacyFile ? 14 : 29;
        Set<String> parsedIds = new HashSet<>();

        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(resource.getInputStream(), "EUC-KR"))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] tokens = splitCsvLine(line);
                if (tokens.length < 12) continue;

                String ykiho = cleanToken(tokens[0]);
                String name = cleanToken(tokens[1]);
                String clCdNm = cleanToken(tokens[3]);
                String sidoCode = cleanToken(tokens[4]);
                String sido = cleanToken(tokens[5]);
                String sggu = cleanToken(tokens[7]);
                String addr = cleanToken(tokens[10]);
                String tel = cleanToken(tokens[11]);

                // 지역 지정 동기화 시 해당 시도만 처리
                if (StringUtils.hasText(regionCode) && !regionCode.equals(sidoCode)) {
                    continue;
                }

                if (!isPharmacyFile && !isDermatologyOrGeneralHospital(name, clCdNm)) {
                    continue;
                }

                String hospitalId = "HIRA-" + ykiho;
                // 같은 파일 내 중복 기관 방지
                if (!parsedIds.add(hospitalId)) {
                    continue;
                }

                HospitalType hospitalType =
                        isPharmacyFile
                                ? HospitalType.PHARMACY
                                : determineHospitalType(clCdNm, name);
                String region = (sido + " " + sggu).trim();
                if (region.isEmpty()) region = "기타";

                HospitalDetailInfo detail = detailMap.get(ykiho);
                boolean isNightCare = detail != null && detail.isNightCare();
                boolean isEmergency =
                        (detail != null && detail.isEmergency())
                                || apiEmergencyNames.contains(name);

                result.add(
                        Hospital.builder()
                                .hospitalId(hospitalId)
                                .name(truncate(name, 100))
                                .hospitalType(hospitalType)
                                .region(truncate(region, 50))
                                .address(truncate(addr, 200))
                                .tel(truncate(tel, 20))
                                .longitude(parseCoordinate(tokens, longitudeIndex))
                                .latitude(parseCoordinate(tokens, latitudeIndex))
                                .weekdayHours(detail != null ? detail.weekdayHours() : null)
                                .saturdayHours(detail != null ? detail.saturdayHours() : null)
                                .sundayHolidayHours(detail != null ? detail.sundayHours() : null)
                                .nightCare(isNightCare)
                                .emergency(isEmergency)
                                .dataSource("공공데이터 포털")
                                .status("ACTIVE")
                                .build());
            }
        } catch (Exception e) {
            log.error("[Sync] {} 파싱 중 에러: {}", fileName, e.getMessage(), e);
            throw new CustomException(
                    ErrorCode.HOSPITAL_SYNC_FAILED, "공공데이터 파일 파싱에 실패했습니다: " + fileName);
        }
        log.info("[Sync] {} 파싱 완료 ({}건)", fileName, result.size());
        return result;
    }

    // 피부과 또는 병원급(종합병원 포함) 의료기관 여부 판별
    private boolean isDermatologyOrGeneralHospital(String name, String clCdNm) {
        if (clCdNm.contains("피부") || name.contains("피부")) return true;
        if (clCdNm.contains("종합병원") || clCdNm.contains("상급종합") || clCdNm.contains("병원"))
            return true;
        return false;
    }

    // 종별 코드명 및 기관명을 기준으로 의료기관 유형(HospitalType) 결정
    private HospitalType determineHospitalType(String clCdNm, String name) {
        if (clCdNm.contains("약국") || name.contains("약국")) {
            return HospitalType.PHARMACY;
        } else if (clCdNm.contains("종합병원") || clCdNm.contains("상급종합")) {
            return HospitalType.GENERAL_HOSPITAL;
        } else if (clCdNm.contains("피부") || name.contains("피부")) {
            return HospitalType.DERMATOLOGY;
        }
        return HospitalType.OTHER;
    }

    // 진료 시작/종료 컬럼으로 진료시간 문자열 생성 (여러 요일이면 가장 이른 시작 ~ 가장 늦은 종료, 정보 없으면 휴무)
    private String formatHours(String[] tokens, int[] startIndices, int[] endIndices) {
        int earliestStart = Integer.MAX_VALUE;
        int latestEnd = -1;
        for (int i = 0; i < startIndices.length; i++) {
            int start = parseHhmm(tokens, startIndices[i]);
            int end = parseHhmm(tokens, endIndices[i]);
            if (start > 0 && end > 0) {
                earliestStart = Math.min(earliestStart, start);
                latestEnd = Math.max(latestEnd, end);
            }
        }
        if (latestEnd < 0) {
            return "휴무";
        }
        return String.format(
                "%02d:%02d - %02d:%02d",
                earliestStart / 100, earliestStart % 100, latestEnd / 100, latestEnd % 100);
    }

    // HHmm 형식 시간 컬럼을 정수로 변환 (ex. "900" → 900, 값이 없거나 형식 오류면 0)
    private int parseHhmm(String[] tokens, int index) {
        if (tokens.length <= index) return 0;
        String digits = cleanToken(tokens[index]).replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 4) return 0;
        return Integer.parseInt(digits);
    }

    // 좌표 컬럼을 실수로 변환 (값이 없거나 형식 오류면 null)
    private Double parseCoordinate(String[] tokens, int index) {
        if (tokens.length <= index) return null;
        String value = cleanToken(tokens[index]);
        if (value.isEmpty()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 컬럼 길이 제한에 맞게 문자열 자르기
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // CSV 한 줄을 컬럼 배열로 분리 (탭 구분 파일은 탭 기준, 그 외에는 따옴표 밖의 쉼표 기준)
    private String[] splitCsvLine(String line) {
        if (line.indexOf('\t') >= 0) {
            return line.split("\t", -1);
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString());

        return tokens.toArray(new String[0]);
    }

    // 진료시간 문자열에서 시(hour) 추출 (ex. "900" → 9, "2000" → 20, "19:30" → 19), 해석 불가 시 -1
    private int parseHour(String timeStr) {
        String digits = timeStr.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 4) {
            return -1;
        }
        int value = Integer.parseInt(digits);
        return digits.length() <= 2 ? value : value / 100;
    }

    // CSV 토큰의 따옴표 제거 및 앞뒤 공백 정리
    private String cleanToken(String token) {
        if (token == null) return "";
        return token.replace("\"", "").trim();
    }

    // 세부정보 CSV에서 추출한 야간진료/응급실 여부 및 진료시간 정보
    private record HospitalDetailInfo(
            boolean isNightCare,
            boolean isEmergency,
            String weekdayHours,
            String saturdayHours,
            String sundayHours) {}
}
