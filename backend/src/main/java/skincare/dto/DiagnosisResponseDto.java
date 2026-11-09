package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.diagnosis.entity.ENUM.WoundType;

import java.time.format.DateTimeFormatter;

// 사용자 AI 분석 결과 응답 DTO
// 목록 조회(diagnoses_list)는 요약 정보만, 상세/분석 요청 응답(diagnosis_result, diagnosis_detail)은 케어 가이드까지 포함
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
// DiagnosisResponseDto 요청과 응답 데이터
public class DiagnosisResponseDto {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private String diagnosisId; // 분석 고유 ID
    private String diagnosedAt; // 분석 일시
    private String imageUrl; // 분석 이미지 조회 API 경로 (본인 인증 필요)
    private BodyPart bodyPart; // 촬영 신체 부위 코드
    private String bodyPartLabel; // 촬영 신체 부위 표시명
    private WoundType aiLabel; // AI 의심 유형 코드
    private String suspectedDisease; // AI 의심 유형 표시 문구 (ex. 화상 의심)
    private RiskLevel riskLevel; // 위험도 코드
    private String riskLevelLabel; // 위험도 표시명 (관찰/경고/응급/판단 불가)
    private Double confidenceScore; // AI 신뢰도 점수 (0.0 ~ 100.0)
    private DiagnosisStatus status; // 처리 상태 코드
    private String statusLabel; // 처리 상태 표시명
    private String symptoms; // 사용자 작성 증상 메모
    private Boolean trainingUse; // 학습 활용 동의 여부
    private CareGuideDto careGuide; // 응급처치 및 케어 가이드 (상세 응답에서만 포함)

    // 사용자 분석 이미지 조회 API 경로 생성
    public static String imagePath(String diagnosisId) {
        return "/api/wc/diagnoses/" + diagnosisId + "/image";
    }

    // 사용자 진단 목록 요약 변환
    public static DiagnosisResponseDto summary(Diagnosis diagnosis) {
        return baseBuilder(diagnosis).build();
    }

    // 상세 조회/분석 요청 응답 변환 (케어 가이드 포함)
    public static DiagnosisResponseDto detail(Diagnosis diagnosis, CareGuideDto careGuide) {
        return baseBuilder(diagnosis)
                .symptoms(diagnosis.getSymptoms())
                .trainingUse(diagnosis.isTrainingUse())
                .careGuide(careGuide)
                .build();
    }

    // 사용자 진단 상세 필드 매핑
    private static DiagnosisResponseDtoBuilder baseBuilder(Diagnosis diagnosis) {
        return DiagnosisResponseDto.builder()
                .diagnosisId(diagnosis.getDiagnosisId())
                .diagnosedAt(
                        diagnosis.getCreatedAt() != null
                                ? diagnosis.getCreatedAt().format(FORMATTER)
                                : null)
                .imageUrl(imagePath(diagnosis.getDiagnosisId()))
                .bodyPart(diagnosis.getBodyPart())
                .bodyPartLabel(
                        diagnosis.getBodyPart() != null ? diagnosis.getBodyPart().getLabel() : null)
                .aiLabel(diagnosis.getAiLabel())
                .suspectedDisease(diagnosis.getSuspectedDisease())
                .riskLevel(diagnosis.getRiskLevel())
                .riskLevelLabel(
                        diagnosis.getRiskLevel() != null
                                ? diagnosis.getRiskLevel().getLabel()
                                : null)
                .confidenceScore(diagnosis.getConfidenceScore())
                .status(diagnosis.getStatus())
                .statusLabel(
                        diagnosis.getStatus() != null ? diagnosis.getStatus().getLabel() : null);
    }
}
