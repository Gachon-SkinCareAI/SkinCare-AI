package skincare.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.ai.entity.AiModelMetrics;

// 관리자 AI 모델 성능 지표 조회 응답 DTO (GET /api/wc/admin/ai/metrics 응답의 metrics_summary 항목)
// metrics_summary는 metric_key를 키로 하는 객체로 응답 (ex. "macro_f1": { "value": 0.86, ... })
@Getter
@Builder
// AiModelMetricResponseDto 요청과 응답 데이터
public class AiModelMetricResponseDto {

    @JsonIgnore
    private String metricKey; // 지표 식별 키 (macro_f1, burn_recall, unknown_rate 등, 응답 객체의 키로 사용)

    private Double value; // 지표 수치 (ex. 0.86)
    private String unit; // 단위 (ex. %)
    private String change; // 변동 수치 (ex. +0.8%)
    private String description; // 지표 항목 설명

    // AiModelMetrics 엔티티를 응답 DTO로 변환
    public static AiModelMetricResponseDto fromEntity(AiModelMetrics entity) {
        return AiModelMetricResponseDto.builder()
                .metricKey(entity.getMetricKey())
                .value(entity.getMetricValue())
                .unit(entity.getUnit())
                .change(entity.getChangeValue())
                .description(entity.getDescription())
                .build();
    }
}
