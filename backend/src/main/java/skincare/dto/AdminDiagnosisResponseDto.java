package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.DiagnosisActionHistory;
import skincare.domain.diagnosis.entity.DiagnosisMetrics;
import skincare.domain.diagnosis.entity.ENUM.*;
import skincare.domain.user.entity.User;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// 관리자 분석 로그 응답 DTO
// 목록 조회(diagnoses_list)는 요약 정보만, 상세 조회(diagnosis_detail)는 사용자 정보/AI 원본 지표/문진/케어 가이드/처리 이력까지 포함
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminDiagnosisResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String diagnosisId;          // 분석 고유 ID
    private String userId;               // 분석 요청 사용자 ID (목록 조회)
    private UserInfo userInfo;           // 분석 요청 사용자 정보 (상세 조회)
    private String createdAt;            // 분석 일시
    private String imageUrl;             // 분석 이미지 조회 API 경로 (관리자 인증 필요)
    private BodyPart bodyPart;           // 촬영 신체 부위
    private WoundType aiLabel;           // AI 의심 유형 코드
    private String suspectedDisease;     // AI 의심 유형 표시 문구
    private RiskLevel riskLevel;         // 위험도
    private String riskLevelLabel;       // 위험도 표시명
    private Double confidenceScore;      // AI 신뢰도 점수 (0.0 ~ 100.0)
    private DiagnosisStatus status;      // 처리 상태
    private String statusLabel;          // 처리 상태 표시명
    private ReviewStatus reviewStatus;   // 검수 상태
    private String reviewStatusLabel;    // 검수 상태 표시명
    private String symptoms;             // 사용자 증상 메모
    private String adminNote;            // 관리자 의견
    private String modelVersion;         // 분석 AI 모델 버전
    private Boolean gatePassed;          // Gate 통과 여부
    private String gateReason;           // Gate 미통과 사유
    private String ruleIds;              // 적용 위험도 규칙 ID
    private Boolean trainingUse;         // 학습 활용 동의 여부
    private Map<String, Object> questionnaire; // 문진 슬롯/응답
    private CareGuideDto careGuide;      // 적용 케어 가이드
    private AiRawMetrics aiRawMetrics;   // AI 원본 분석 지표
    private List<ActionHistory> actionHistory; // 관리자 처리 이력
    private String reviewedBy;           // 마지막 처리 관리자
    private String reviewedAt;           // 마지막 처리 일시

    // 분석 요청 사용자 정보
    @Getter
    @Builder
    public static class UserInfo {
        private String userId;   // 사용자 ID
        private String name;     // 닉네임
        private String email;    // 이메일
        private String planType; // 이용 플랜
    }

    // AI 원본 분석 지표
    @Getter
    @Builder
    public static class AiRawMetrics {
        private String boundingBox;      // 환부 바운딩 박스 [x, y, w, h]
        private Double detectConfidence; // 환부 탐지 Confidence
        private Double energyScore;      // OOD Energy 점수
        private String inferenceTime;    // 추론 소요 시간
    }

    // 관리자 처리 이력
    @Getter
    @Builder
    public static class ActionHistory {
        private DiagnosisStatus previousStatus; // 변경 전 처리 상태
        private DiagnosisStatus currentStatus;  // 변경 후 처리 상태
        private ReviewStatus reviewStatus;      // 검수 상태
        private String note;                    // 관리자 의견
        private String processedBy;             // 처리 관리자
        private String processedAt;             // 처리 일시
    }

    // 관리자 분석 이미지 조회 API 경로 생성
    public static String imagePath(String diagnosisId) {
        return "/api/wc/admin/diagnoses/" + diagnosisId + "/image";
    }

    // 목록 조회용 요약 응답 변환
    public static AdminDiagnosisResponseDto summary(Diagnosis diagnosis) {
        return baseBuilder(diagnosis)
                .userId(diagnosis.getUser().getUserId())
                .build();
    }

    // 상세 조회용 응답 변환
    public static AdminDiagnosisResponseDto detail(Diagnosis diagnosis, CareGuideDto careGuide,
                                                   Map<String, Object> questionnaire) {
        User user = diagnosis.getUser();
        DiagnosisMetrics m = diagnosis.getMetrics();

        return baseBuilder(diagnosis)
                .userInfo(UserInfo.builder()
                        .userId(user.getUserId())
                        .name(user.getNickname())
                        .email(user.getEmail())
                        .planType(user.getPlan() != null ? user.getPlan().name() : null)
                        .build())
                .symptoms(diagnosis.getSymptoms())
                .adminNote(diagnosis.getAdminNote())
                .modelVersion(diagnosis.getModelVersion())
                .gatePassed(diagnosis.getGatePassed())
                .gateReason(diagnosis.getGateReason())
                .ruleIds(diagnosis.getRuleIds())
                .trainingUse(diagnosis.isTrainingUse())
                .questionnaire(questionnaire)
                .careGuide(careGuide)
                .aiRawMetrics(m == null ? null : AiRawMetrics.builder()
                        .boundingBox(m.getBoundingBox())
                        .detectConfidence(m.getDetectConfidence())
                        .energyScore(m.getEnergyScore())
                        .inferenceTime(m.getInferenceTime())
                        .build())
                .actionHistory(diagnosis.getActionHistories().stream().map(AdminDiagnosisResponseDto::toActionHistory).toList())
                .reviewedBy(diagnosis.getReviewedBy())
                .reviewedAt(diagnosis.getReviewedAt() != null ? diagnosis.getReviewedAt().format(FORMATTER) : null)
                .build();
    }

    // 처리 이력 엔티티를 응답 객체로 변환
    public static ActionHistory toActionHistory(DiagnosisActionHistory history) {
        return ActionHistory.builder()
                .previousStatus(history.getPreviousStatus())
                .currentStatus(history.getCurrentStatus())
                .reviewStatus(history.getReviewStatus())
                .note(history.getNote())
                .processedBy(history.getProcessedBy())
                .processedAt(history.getCreatedAt() != null ? history.getCreatedAt().format(FORMATTER) : null)
                .build();
    }

    // 목록/상세 공통 필드 매핑
    private static AdminDiagnosisResponseDtoBuilder baseBuilder(Diagnosis diagnosis) {
        return AdminDiagnosisResponseDto.builder()
                .diagnosisId(diagnosis.getDiagnosisId())
                .createdAt(diagnosis.getCreatedAt() != null ? diagnosis.getCreatedAt().format(FORMATTER) : null)
                .imageUrl(imagePath(diagnosis.getDiagnosisId()))
                .bodyPart(diagnosis.getBodyPart())
                .aiLabel(diagnosis.getAiLabel())
                .suspectedDisease(diagnosis.getSuspectedDisease())
                .riskLevel(diagnosis.getRiskLevel())
                .riskLevelLabel(diagnosis.getRiskLevel() != null ? diagnosis.getRiskLevel().getLabel() : null)
                .confidenceScore(diagnosis.getConfidenceScore())
                .status(diagnosis.getStatus())
                .statusLabel(diagnosis.getStatus() != null ? diagnosis.getStatus().getLabel() : null)
                .reviewStatus(diagnosis.getReviewStatus())
                .reviewStatusLabel(diagnosis.getReviewStatus() != null ? diagnosis.getReviewStatus().getLabel() : null);
    }
}
