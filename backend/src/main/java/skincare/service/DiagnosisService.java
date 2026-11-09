package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.DiagnosisMetrics;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.diagnosis.entity.ENUM.WoundType;
import skincare.domain.global.IdGenerator;
import skincare.domain.user.entity.User;
import skincare.dto.CareGuideDto;
import skincare.dto.DiagnosisResponseDto;
import skincare.dto.Paging;
import skincare.dto.StoredFileDto;
import skincare.dto.ai.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.AiServiceClient;
import skincare.repository.DiagnosisRepository;
import skincare.repository.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

// 사용자 AI 피부·상처 분석(Diagnosis) 관련 비즈니스 로직 처리
// 분석 흐름: 이미지 저장 → predict(이미지 분석) → analyze(위험도 평가) → guide(공인 케어 카드) → 결과 저장
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// DiagnosisService 비즈니스 로직 처리
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository; // 분석 기록 데이터 베이스 접근용 Repository
    private final UserRepository userRepository; // 사용자 데이터 베이스 접근용 Repository
    private final AiServiceClient aiServiceClient; // AI 서버 연동 클라이언트
    private final FileStorageService fileStorageService; // 상처 사진 파일 저장소
    private final JsonConverter jsonConverter; // 케어 카드/문진 JSON 변환

    // 상처 이미지 업로드 및 AI 분석 요청
    // AI 서버 호출(최대 수 초) 동안 DB 커넥션을 점유하지 않도록 트랜잭션 없이 실행하고, 저장 시점에만 트랜잭션 사용
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DiagnosisResponseDto analyzeWound(
            String userId,
            MultipartFile imageFile,
            BodyPart bodyPart,
            String symptoms,
            boolean trainingUse) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. 이미지 형식 검증 및 파일 저장소 저장
        String imageKey = fileStorageService.storeImage(imageFile, "diagnoses");

        try {
            // 2. 이미지 분석 (품질 검사 → 환부 탐지 → 유형 분류 → Confidence/OOD Gate)
            AiPredictResponseDto predict = aiServiceClient.predict(imageFile, bodyPart);
            boolean gatePassed = Boolean.TRUE.equals(predict.gatePassed());
            WoundType woundType =
                    gatePassed ? WoundType.fromCode(predict.label()) : WoundType.UNKNOWN;

            // 3. 문진 슬롯 구성 (동적 문진 API 연동 전까지는 사용자가 입력한 증상 메모를 슬롯으로 전달)
            Map<String, Object> slots = new LinkedHashMap<>();
            if (StringUtils.hasText(symptoms)) {
                slots.put("symptom_text", symptoms.trim());
            }

            // 4. 위험도 평가 (응급 규칙 우선, Gate 미통과 시 UNKNOWN)
            AiAnalyzeResponseDto analyze =
                    aiServiceClient.analyze(
                            new AiAnalyzeRequestDto(
                                    predict.requestId(),
                                    woundType.getCode(),
                                    predict.confidence(),
                                    gatePassed,
                                    slots));

            // 5. 공인 케어 카드 조회 (조회 실패 시에도 위험도 결과는 저장하고 안내 문구로 대체)
            AiGuideResponseDto guide = fetchGuide(analyze, slots);

            // 6. 분석 결과 저장
            Diagnosis saved =
                    saveDiagnosis(
                            user,
                            imageKey,
                            bodyPart,
                            symptoms,
                            trainingUse,
                            predict,
                            woundType,
                            gatePassed,
                            analyze,
                            guide,
                            slots);

            log.info(
                    "[Analysis] AI 상처 분석 완료 (diagnosis_id: {}, label: {}, risk_level: {})",
                    saved.getDiagnosisId(),
                    woundType.getCode(),
                    saved.getRiskLevel());

            return DiagnosisResponseDto.detail(saved, CareGuideDto.from(guide));

        } catch (RuntimeException e) {
            // 분석/저장 실패 시 저장해 둔 이미지 파일 정리
            fileStorageService.deleteQuietly(imageKey);
            throw e;
        }
    }

    // 본인 분석 이력 목록 조회 (페이지네이션, 위험도 필터)
    public Page<DiagnosisResponseDto> getMyDiagnosisHistory(
            String userId, RiskLevel riskLevel, int page, int size) {
        Pageable pageable = Paging.of(page, size);
        Page<Diagnosis> diagnoses =
                (riskLevel == null)
                        ? diagnosisRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable)
                        : diagnosisRepository.findByUserUserIdAndRiskLevelOrderByCreatedAtDesc(
                                userId, riskLevel, pageable);

        return diagnoses.map(DiagnosisResponseDto::summary);
    }

    // 분석 결과 및 케어 가이드 상세 조회
    public DiagnosisResponseDto getDiagnosisDetail(String userId, String diagnosisId) {
        Diagnosis diagnosis = findMyDiagnosis(userId, diagnosisId);
        return DiagnosisResponseDto.detail(diagnosis, readCareGuide(diagnosis));
    }

    // 본인 분석 이미지 조회
    public StoredFileDto getDiagnosisImage(String userId, String diagnosisId) {
        Diagnosis diagnosis = findMyDiagnosis(userId, diagnosisId);
        return new StoredFileDto(
                fileStorageService.load(diagnosis.getImageUrl()),
                fileStorageService.getMediaType(diagnosis.getImageUrl()));
    }

    // 분석 사진 학습 활용 동의/철회 (동의한 분석만 재학습 후보로 사용, 설계서 6.3.8)
    @Transactional
    public Map<String, Object> updateTrainingUse(
            String userId, String diagnosisId, boolean trainingUse) {
        Diagnosis diagnosis = findMyDiagnosis(userId, diagnosisId);
        diagnosis.updateTrainingUse(trainingUse);
        log.info(
                "[Analysis] 정상 처리, 학습 활용 동의 변경 (diagnosis_id: {}, training_use: {})",
                diagnosisId,
                trainingUse);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosis_id", diagnosisId);
        result.put("training_use", trainingUse);
        return result;
    }

    // AI 분석 결과 피드백 전송 (AI 서버 피드백 큐 적재, 추론/모델 교체는 하지 않음)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> sendFeedback(
            String userId, String diagnosisId, String kind, String note) {
        Diagnosis diagnosis = findMyDiagnosis(userId, diagnosisId);
        if (!StringUtils.hasText(diagnosis.getAiRequestId())) {
            throw new CustomException(
                    ErrorCode.INVALID_AI_REQUEST, "AI 분석 요청 정보가 없어 피드백을 전송할 수 없습니다.");
        }

        // 사진 파일 본문은 전송하지 않고 앱 서버 사진 ID(분석 ID)만 전달
        AiFeedbackResponseDto response =
                aiServiceClient.feedback(
                        new AiFeedbackRequestDto(
                                diagnosis.getAiRequestId(), kind, note, diagnosisId));
        log.info(
                "[Analysis] 정상 처리, 분석 결과 피드백 전송 (diagnosis_id: {}, kind: {}, queued: {})",
                diagnosisId,
                kind,
                response.queued());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosis_id", diagnosisId);
        result.put("queued", response.queued());
        result.put("queue_id", response.queueId());
        result.put("training_use", diagnosis.isTrainingUse());
        return result;
    }

    // 저장된 케어 카드 JSON을 케어 가이드 응답으로 변환 (관리자 상세 조회에서도 사용)
    public CareGuideDto readCareGuide(Diagnosis diagnosis) {
        AiGuideResponseDto guide =
                jsonConverter.fromJson(diagnosis.getCareCards(), AiGuideResponseDto.class);
        if (guide == null) {
            // 케어 카드 JSON이 없는 기존 데이터는 텍스트 가이드만 제공
            return CareGuideDto.builder()
                    .firstAid(diagnosis.getFirstAidGuide())
                    .precautions(diagnosis.getPrecautionsGuide())
                    .build();
        }
        return CareGuideDto.from(guide);
    }

    // 본인 분석 기록 조회 (타인 기록은 존재 여부를 노출하지 않고 동일하게 404 처리)
    private Diagnosis findMyDiagnosis(String userId, String diagnosisId) {
        return diagnosisRepository
                .findByDiagnosisIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND));
    }

    // 공인 케어 카드 조회 (실패 시 카드 없이 사유만 기록, 문진 완료 후 재평가에서도 사용)
    AiGuideResponseDto fetchGuide(AiAnalyzeResponseDto analyze, Map<String, Object> slots) {
        try {
            return aiServiceClient.guide(
                    new AiGuideRequestDto(analyze.level(), analyze.ruleIds(), slots, null));
        } catch (CustomException e) {
            log.warn("[Analysis] 케어 가이드 조회 실패, 가이드 없이 분석 결과 저장: {}", e.getMessage());
            return new AiGuideResponseDto(List.of(), List.of(), "guide_unavailable");
        }
    }

    // 분석 결과 엔티티 생성 및 저장
    private Diagnosis saveDiagnosis(
            User user,
            String imageKey,
            BodyPart bodyPart,
            String symptoms,
            boolean trainingUse,
            AiPredictResponseDto predict,
            WoundType woundType,
            boolean gatePassed,
            AiAnalyzeResponseDto analyze,
            AiGuideResponseDto guide,
            Map<String, Object> slots) {
        String diagnosisId = IdGenerator.generate("DX");
        List<AiGuideResponseDto.CareCard> cards = guide.cards() != null ? guide.cards() : List.of();
        List<AiGuideResponseDto.AvoidItem> avoid =
                guide.avoid() != null ? guide.avoid() : List.of();

        Diagnosis diagnosis =
                Diagnosis.builder()
                        .diagnosisId(diagnosisId)
                        .user(user)
                        .imageUrl(imageKey)
                        .bodyPart(bodyPart)
                        .aiLabel(woundType)
                        .suspectedDisease(woundType.getLabel())
                        .riskLevel(analyze.level())
                        .confidenceScore(toPercent(predict.confidence()))
                        .gatePassed(gatePassed)
                        .gateReason(predict.reason())
                        .aiRequestId(predict.requestId())
                        .modelVersion(formatModelVersion(predict.modelVersion()))
                        .ruleIds(joinOrNull(analyze.ruleIds()))
                        .appliedCardIds(
                                joinOrNull(
                                        cards.stream()
                                                .map(AiGuideResponseDto.CareCard::id)
                                                .toList()))
                        .careCards(jsonConverter.toJson(guide))
                        .questionnaire(
                                jsonConverter.toJson(
                                        Map.of(
                                                "slots",
                                                slots,
                                                "missing_slots",
                                                analyze.missingSlots() != null
                                                        ? analyze.missingSlots()
                                                        : List.of())))
                        .trainingUse(trainingUse)
                        // 응급 위험도는 관리자 확인이 필요하므로 대기(PENDING) 상태로 등록
                        .status(
                                analyze.level() == RiskLevel.EMERGENCY
                                        ? DiagnosisStatus.PENDING
                                        : DiagnosisStatus.COMPLETED)
                        .symptoms(symptoms)
                        .firstAidGuide(CareGuideDto.joinFirstAid(cards))
                        .precautionsGuide(CareGuideDto.joinPrecautions(avoid))
                        .build();

        // DiagnosisMetrics (@MapsId 공유 PK 구조 매핑)
        DiagnosisMetrics metrics =
                DiagnosisMetrics.builder()
                        .diagnosisId(diagnosisId)
                        .diagnosis(diagnosis)
                        .boundingBox(predict.bbox() != null ? predict.bbox().toString() : null)
                        .detectConfidence(predict.detectConfidence())
                        .energyScore(predict.energyScore())
                        .inferenceTime(
                                predict.inferenceMs() != null ? predict.inferenceMs() + "ms" : null)
                        .build();
        diagnosis.initMetrics(metrics);

        try {
            return diagnosisRepository.save(diagnosis);
        } catch (RuntimeException e) {
            log.error(
                    "[Analysis] 분석 결과 저장 실패 (diagnosis_id: {}): {}",
                    diagnosisId,
                    e.getMessage(),
                    e);
            throw new CustomException(ErrorCode.DIAGNOSIS_SAVE_FAILED);
        }
    }

    // AI Confidence(0~1)를 백분율(0~100, 소수점 첫째 자리)로 변환 (이미 백분율이면 그대로 사용)
    private Double toPercent(Double confidence) {
        if (confidence == null) {
            return 0.0;
        }
        double percent = confidence <= 1.0 ? confidence * 100 : confidence;
        return Math.round(percent * 10) / 10.0;
    }

    // 모델 버전 Map을 저장용 문자열로 변환 (ex. detect=yolov8n-...,classify=vit-...)
    private String formatModelVersion(Map<String, String> modelVersion) {
        if (modelVersion == null || modelVersion.isEmpty()) {
            return null;
        }
        return modelVersion.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    // 문자열 목록을 쉼표로 연결 (비어 있으면 null)
    static String joinOrNull(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }
}
