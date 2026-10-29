package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;
import skincare.dto.HospitalSyncRequestDto;
import skincare.dto.HospitalSyncResponseDto;
import skincare.external.MedicalApiClient;
import skincare.repository.HospitalRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalSyncService {

    private final HospitalRepository hospitalRepository;
    private final MedicalApiClient medicalApiClient;

    @Transactional
    public HospitalSyncResponseDto syncHospitals(HospitalSyncRequestDto dto) {
        long startTime = System.currentTimeMillis();
        String syncId = "SYNC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        int totalFetched = 0;
        int savedCount = 0;

        try {
            // 1. 국립중앙의료원 OpenAPI 응급실 정보 수집
            log.info("[Sync] 국립중앙의료원 응급실 정보 수집 시작...");
            Set<String> apiEmergencyHospitalNames = medicalApiClient.fetchEmergencyHospitalNames();

            // 2. 세부정보 CSV (진료시간 및 야간응급 여부) 맵 구성
            log.info("[Sync] hospital_details.csv (세부정보) 읽기 시작...");
            Map<String, HospitalDetailInfo> detailMap = loadHospitalDetails();

            // 3. 기존 DB 데이터의 ID 및 상태 목록 한 번에 가져오기 (단건 SELECT 발생 차단)
            log.info("[Sync] 기존 DB 데이터 식별자 조회 중...");
            Set<String> existingIds = new HashSet<>(hospitalRepository.findAllIds());

            List<Hospital> hospitalsToSave = new ArrayList<>();

            // 4. 병원 CSV 파싱
            log.info("[Sync] hospitals.csv 파싱 시작...");
            parseCsvFile("/static/hospitals.csv", false, apiEmergencyHospitalNames, detailMap, existingIds, hospitalsToSave);

            // 5. 약국 CSV 파싱
            log.info("[Sync] pharmacies.csv 파싱 시작...");
            parseCsvFile("/static/pharmacies.csv", true, apiEmergencyHospitalNames, detailMap, existingIds, hospitalsToSave);

            totalFetched = hospitalsToSave.size();
            log.info("[Sync] 대량 DB 저장 시작 (총 {}건)...", totalFetched);

            // 6. saveAll()을 통한 Batch Insert (1,000건씩 분할 저장)
            int batchSize = 1000;
            for (int i = 0; i < hospitalsToSave.size(); i += batchSize) {
                int end = Math.min(i + batchSize, hospitalsToSave.size());
                hospitalRepository.saveAll(hospitalsToSave.subList(i, end));
                hospitalRepository.flush(); // 배치 플러시
            }

            savedCount = totalFetched;

        } catch (Exception e) {
            log.error("[Sync] 동기화 처리 도중 에러 발생: {}", e.getMessage(), e);
        }

        double durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        log.info("[Sync 완료] 총 DB 반영: {}건, 소요시간: {}초", savedCount, durationSeconds);

        return HospitalSyncResponseDto.builder()
                .syncId(syncId)
                .targetScope(dto.getTargetScope() != null ? dto.getTargetScope() : "ALL")
                .executedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .durationSeconds(durationSeconds)
                .metrics(HospitalSyncResponseDto.SyncMetrics.builder()
                        .totalFetched(savedCount)
                        .newAdded(savedCount)
                        .updated(0)
                        .deactivated(0)
                        .build())
                .status("COMPLETED")
                .build();
    }

    // 세부정보 CSV 파싱
    private Map<String, HospitalDetailInfo> loadHospitalDetails() {
        Map<String, HospitalDetailInfo> map = new HashMap<>();
        ClassPathResource resource = new ClassPathResource("/static/hospital_details.csv");

        if (!resource.exists()) {
            return map;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), "EUC-KR"))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] tokens = line.split("\t", -1);
                if (tokens.length < 14) continue;

                String ykiho = cleanToken(tokens[0]);
                String nightErmnyYn = tokens.length > 13 ? cleanToken(tokens[13]) : "";

                boolean isNightCare = false;
                int[] closeTimeIndices = {23, 25, 27, 29, 31};
                for (int idx : closeTimeIndices) {
                    if (tokens.length > idx) {
                        String timeStr = cleanToken(tokens[idx]).replaceAll("[^0-9]", "");
                        if (timeStr.length() >= 2) {
                            int hour = Integer.parseInt(timeStr.substring(0, 2));
                            if (hour >= 19) {
                                isNightCare = true;
                                break;
                            }
                        }
                    }
                }

                boolean isEmergency = "Y".equalsIgnoreCase(nightErmnyYn);
                map.put(ykiho, new HospitalDetailInfo(isNightCare, isEmergency));
            }
        } catch (Exception e) {
            log.error("hospital_details.csv 읽기 실패: {}", e.getMessage());
        }
        return map;
    }

    // CSV 파일 파싱 후 저장용 엔티티 리스트 생성 (DB 조회를 반복문 안에서 하지 않음)
    private void parseCsvFile(String fileName, boolean isPharmacyFile, Set<String> apiEmergencyNames,
                              Map<String, HospitalDetailInfo> detailMap, Set<String> existingIds, List<Hospital> targetList) {
        ClassPathResource resource = new ClassPathResource(fileName);
        if (!resource.exists()) {
            log.error("파일이 자원에 없습니다: {}", fileName);
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), "EUC-KR"))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] tokens = line.split("\t", -1);
                if (tokens.length < 10) {
                    tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                }
                if (tokens.length < 10) continue;

                String ykiho = cleanToken(tokens[0]);
                String name = cleanToken(tokens[1]);
                String clCdNm = cleanToken(tokens[3]);
                String sido = cleanToken(tokens[5]);
                String sggu = cleanToken(tokens[7]);
                String addr = cleanToken(tokens[10]);
                String tel = cleanToken(tokens[11]);

                if (!isPharmacyFile && !isDermatologyOrGeneralHospital(name, clCdNm)) {
                    continue;
                }

                HospitalType hospitalType = isPharmacyFile ? HospitalType.PHARMACY : determineHospitalType(clCdNm, name);
                String region = (sido + " " + sggu).trim();
                if (region.isEmpty()) region = "기타";

                HospitalDetailInfo detail = detailMap.get(ykiho);
                boolean isNightCare = detail != null && detail.isNightCare();
                boolean isEmergency = (detail != null && detail.isEmergency()) || apiEmergencyNames.contains(name);

                String hospitalId = "HIRA-" + ykiho;

                // 이미 가공 데이터 리스트에 추가된 경우 중복 방지
                if (existingIds.contains(hospitalId)) {
                    continue;
                }
                existingIds.add(hospitalId);

                Hospital hospital = Hospital.builder()
                        .hospitalId(hospitalId)
                        .name(name)
                        .hospitalType(hospitalType)
                        .region(region)
                        .address(addr)
                        .tel(tel)
                        .nightCare(isNightCare)
                        .emergency(isEmergency)
                        .dataSource("공공데이터 포털")
                        .status("ACTIVE")
                        .build();

                targetList.add(hospital);
            }
        } catch (Exception e) {
            log.error("{} 파싱 중 에러: {}", fileName, e.getMessage(), e);
        }
    }

    private boolean isDermatologyOrGeneralHospital(String name, String clCdNm) {
        if (clCdNm.contains("피부") || name.contains("피부")) return true;
        if (clCdNm.contains("종합병원") || clCdNm.contains("상급종합") || clCdNm.contains("병원")) return true;
        return false;
    }

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

    private String cleanToken(String token) {
        if (token == null) return "";
        return token.replace("\"", "").trim();
    }

    private record HospitalDetailInfo(boolean isNightCare, boolean isEmergency) {}
}