package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.DiagnosisMetrics;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class DiagnosisResponseDto {

    private String diagnosisId;
    private String imageUrl;
    private BodyPart bodyPart;
    private String suspectedDisease;
    private RiskLevel riskLevel;
    private Double confidenceScore;
    private DiagnosisStatus status;
    private String symptoms;
    private String firstAidGuide;
    private String precautionsGuide;
    private String adminNote;

    // AI 세부 분석 메트릭 (DiagnosisMetrics)
    private MetricsDto metrics;

    private String createdAt;

    @Getter
    @Builder
    public static class MetricsDto {
        private String affectedAreaSize;
        private Double segmentationIou;
        private String boundingBox;
        private String inferenceTime;
    }

    public static DiagnosisResponseDto fromEntity(Diagnosis diagnosis) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DiagnosisMetrics m = diagnosis.getMetrics();

        MetricsDto metricsDto = null;
        if (m != null) {
            metricsDto = MetricsDto.builder()
                    .affectedAreaSize(m.getAffectedAreaSize())
                    .segmentationIou(m.getSegmentationIou())
                    .boundingBox(m.getBoundingBox())
                    .inferenceTime(m.getInferenceTime())
                    .build();
        }

        return DiagnosisResponseDto.builder()
                .diagnosisId(diagnosis.getDiagnosisId())
                .imageUrl(diagnosis.getImageUrl())
                .bodyPart(diagnosis.getBodyPart())
                .suspectedDisease(diagnosis.getSuspectedDisease())
                .riskLevel(diagnosis.getRiskLevel())
                .confidenceScore(diagnosis.getConfidenceScore())
                .status(diagnosis.getStatus())
                .symptoms(diagnosis.getSymptoms())
                .firstAidGuide(diagnosis.getFirstAidGuide())
                .precautionsGuide(diagnosis.getPrecautionsGuide())
                .adminNote(diagnosis.getAdminNote())
                .metrics(metricsDto)
                .createdAt(diagnosis.getCreatedAt() != null ? diagnosis.getCreatedAt().format(formatter) : null)
                .build();
    }
}