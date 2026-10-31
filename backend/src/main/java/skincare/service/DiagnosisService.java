package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.DiagnosisMetrics;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.user.entity.User;
import skincare.dto.AiInferenceResponseDto;
import skincare.dto.DiagnosisRequestDto;
import skincare.dto.DiagnosisResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.AiInferenceClient;
import skincare.repository.DiagnosisRepository;
import skincare.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository;
    private final UserRepository userRepository;
    private final AiInferenceClient aiInferenceClient;

    // 상처 이미지 업로드 및 AI 분석 요청
    @Transactional
    public DiagnosisResponseDto analyzeWound(String userId, MultipartFile imageFile, DiagnosisRequestDto requestDto) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // TODO: S3/로컬 파일 저장 처리 (업로드된 이미지 저장 경로)
        String uploadedImageUrl = "https://cdn.skincare-ai.com/uploads/diagnoses/" + System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();

        // AI 서버 추론 요청
        AiInferenceResponseDto aiResult = aiInferenceClient.requestInference(imageFile, requestDto.getBodyPart());

        // 진단 고유 ID 생성 (ex. DX-20261031-153022-123)
        String diagnosisId = "DX-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + (int)(Math.random() * 900 + 100);

        // Diagnosis 엔티티 빌드
        Diagnosis diagnosis = Diagnosis.builder()
                .diagnosisId(diagnosisId)
                .user(user)
                .imageUrl(uploadedImageUrl)
                .bodyPart(requestDto.getBodyPart())
                .suspectedDisease(aiResult.getSuspectedDisease())
                .riskLevel(aiResult.getRiskLevel())
                .confidenceScore(aiResult.getConfidenceScore())
                .status(DiagnosisStatus.COMPLETED)
                .symptoms(requestDto.getSymptoms())
                .firstAidGuide(aiResult.getFirstAidGuide())
                .precautionsGuide(aiResult.getPrecautionsGuide())
                .build();

        // DiagnosisMetrics (@MapsId 공유 PK 구조 매핑)
        DiagnosisMetrics metrics = DiagnosisMetrics.builder()
                .diagnosisId(diagnosisId)
                .diagnosis(diagnosis)
                .affectedAreaSize(aiResult.getAffectedAreaSize())
                .segmentationIou(aiResult.getSegmentationIou())
                .boundingBox(aiResult.getBoundingBox())
                .inferenceTime(aiResult.getInferenceTime())
                .build();

        diagnosis.initMetrics(metrics);

        Diagnosis savedDiagnosis = diagnosisRepository.save(diagnosis);
        log.info("[Diagnosis] AI 피부 진단 완료 - UserId: {}, DiagnosisId: {}, SuspectedDisease: {}",
                userId, diagnosisId, aiResult.getSuspectedDisease());

        return DiagnosisResponseDto.fromEntity(savedDiagnosis);
    }

    // 본인 진단 이력 목록 조회 (페이지네이션)
    public Page<DiagnosisResponseDto> getMyDiagnosisHistory(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Diagnosis> diagnoses = diagnosisRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable);

        return diagnoses.map(DiagnosisResponseDto::fromEntity);
    }

    // 진단 결과 및 케어 가이드 상세 조회
    public DiagnosisResponseDto getDiagnosisDetail(String userId, String diagnosisId) {
        Diagnosis diagnosis = diagnosisRepository.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND, "해당 진단 기록을 찾을 수 없거나 접근 권한이 없습니다."));

        return DiagnosisResponseDto.fromEntity(diagnosis);
    }
}