package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.Recovery;
import skincare.domain.global.IdGenerator;
import skincare.dto.RecoveryGroupResponseDto;
import skincare.dto.RecoveryResponseDto;
import skincare.dto.StoredFileDto;
import skincare.dto.ai.AiTrackingResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.AiServiceClient;
import skincare.repository.DiagnosisRepository;
import skincare.repository.RecoveryRepository;

import java.util.*;

// 상처 회복 경과 기록 관련 비즈니스 로직 처리
// 처리 흐름: 기존 상처(분석 기록) 선택 → 새 사진/통증 점수/메모 등록 → 최초 사진과 AI 면적 비교(참고값) → 타임라인 반영
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecoveryService {

    private static final int MIN_PAIN_SCORE = 0;
    private static final int MAX_PAIN_SCORE = 10;

    private final RecoveryRepository recoveryRepository;   // 회복 기록 데이터 베이스 접근용 Repository
    private final DiagnosisRepository diagnosisRepository; // 분석 기록 데이터 베이스 접근용 Repository
    private final FileStorageService fileStorageService;   // 경과 사진 파일 저장소
    private final AiServiceClient aiServiceClient;         // AI 서버 사진 비교(/tracking) 연동

    // 회복 경과 기록 등록 (AI 사진 비교 호출 동안 DB 커넥션을 점유하지 않도록 트랜잭션 없이 실행)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RecoveryResponseDto createRecovery(String userId, String diagnosisId, MultipartFile image,
                                              String memo, Integer painScore) {
        // 통증 점수 범위 검증 (0 ~ 10)
        if (painScore != null && (painScore < MIN_PAIN_SCORE || painScore > MAX_PAIN_SCORE)) {
            throw new CustomException(ErrorCode.INVALID_PAIN_SCORE);
        }

        // 본인 분석 기록만 경과 기록 연결 가능
        Diagnosis diagnosis = diagnosisRepository.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARENT_DIAGNOSIS_NOT_FOUND));

        String imageKey = fileStorageService.storeImage(image, "recoveries");
        try {
            // 최초 분석 사진과 면적 변화 비교 (AI 서버 오류 시에도 기록 저장은 진행)
            AiTrackingResponseDto tracking = compareWithInitialImage(diagnosis, image);

            Recovery recovery = Recovery.builder()
                    .recoveryId(IdGenerator.generate("REC"))
                    .diagnosis(diagnosis)
                    .imageUrl(imageKey)
                    .memo(memo)
                    .painScore(painScore)
                    .areaRatioDelta(tracking != null ? tracking.areaRatioDelta() : null)
                    .trendHint(tracking != null ? tracking.trendHint() : null)
                    .trackingNote(tracking != null ? tracking.note() : "AI 사진 비교를 일시적으로 이용할 수 없어 비교 결과 없이 저장되었습니다.")
                    .build();

            Recovery saved = recoveryRepository.saveAndFlush(recovery);
            log.info("[Analysis] 정상 처리, 상처 회복 경과 기록 등록 완료 (recovery_id: {}, diagnosis_id: {})",
                    saved.getRecoveryId(), diagnosisId);
            return RecoveryResponseDto.from(saved);

        } catch (CustomException e) {
            fileStorageService.deleteQuietly(imageKey);
            throw e;
        } catch (RuntimeException e) {
            fileStorageService.deleteQuietly(imageKey);
            log.error("[Analysis] 회복 기록 저장 실패 (diagnosis_id: {}): {}", diagnosisId, e.getMessage(), e);
            throw new CustomException(ErrorCode.RECOVERY_SAVE_FAILED);
        }
    }

    // 상처별 회복 경과 목록 조회 (신체 부위 / 특정 분석 기록 필터)
    public List<RecoveryGroupResponseDto> getMyRecoveries(String userId, BodyPart bodyPart, String diagnosisId) {
        // 분석 기록별로 회복 기록 묶기 (회복 기록은 최신순, 묶음은 최근 기록이 있는 상처 순)
        Map<String, List<Recovery>> grouped = new LinkedHashMap<>();
        Map<String, Diagnosis> diagnoses = new HashMap<>();
        for (Recovery recovery : recoveryRepository.findAllByUserId(userId)) {
            Diagnosis diagnosis = recovery.getDiagnosis();
            if (bodyPart != null && diagnosis.getBodyPart() != bodyPart) continue;
            if (diagnosisId != null && !diagnosis.getDiagnosisId().equals(diagnosisId)) continue;

            grouped.computeIfAbsent(diagnosis.getDiagnosisId(), key -> new ArrayList<>()).add(recovery);
            diagnoses.putIfAbsent(diagnosis.getDiagnosisId(), diagnosis);
        }

        return grouped.entrySet().stream()
                .map(entry -> RecoveryGroupResponseDto.of(diagnoses.get(entry.getKey()),
                        entry.getValue().stream().map(RecoveryResponseDto::from).toList()))
                .toList();
    }

    // 본인 경과 사진 조회
    public StoredFileDto getRecoveryImage(String userId, String recoveryId) {
        Recovery recovery = findMyRecovery(userId, recoveryId);
        return new StoredFileDto(fileStorageService.load(recovery.getImageUrl()),
                fileStorageService.getMediaType(recovery.getImageUrl()));
    }

    // 본인 회복 기록 삭제 (경과 사진 파일 포함)
    @Transactional
    public void deleteRecovery(String userId, String recoveryId) {
        Recovery recovery = findMyRecovery(userId, recoveryId);
        String imageKey = recovery.getImageUrl();
        recoveryRepository.delete(recovery);
        recoveryRepository.flush();
        fileStorageService.deleteQuietly(imageKey);
        log.info("[Analysis] 정상 처리, 회복 경과 기록 삭제 (recovery_id: {})", recoveryId);
    }

    // 최초 분석 사진과 새 경과 사진의 면적 변화 비교 (실패 시 null)
    private AiTrackingResponseDto compareWithInitialImage(Diagnosis diagnosis, MultipartFile image) {
        try {
            return aiServiceClient.tracking(fileStorageService.load(diagnosis.getImageUrl()), image);
        } catch (CustomException e) {
            log.warn("[Analysis] AI 사진 비교 실패, 비교 결과 없이 회복 기록 저장 (diagnosis_id: {}): {}",
                    diagnosis.getDiagnosisId(), e.getMessage());
            return null;
        }
    }

    // 본인 회복 기록 조회 (타인 기록은 존재 여부를 노출하지 않고 RECOVERY-001)
    private Recovery findMyRecovery(String userId, String recoveryId) {
        return recoveryRepository.findByRecoveryIdAndUserId(recoveryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECOVERY_NOT_FOUND));
    }
}
