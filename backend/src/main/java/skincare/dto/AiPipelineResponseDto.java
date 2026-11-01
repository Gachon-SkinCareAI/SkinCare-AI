package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.ai.entity.AiPipeline;

// 관리자 AI 파이프라인 엔진별 상태 조회 응답 DTO (GET /api/wc/admin/ai/pipelines)
@Getter
@Builder
public class AiPipelineResponseDto {

    private Long id;                   // 파이프라인 식별자
    private String engineName;         // YOLOv8_Segmentation, ViT_Classifier 등
    private String status;             // HEALTHY, UNHEALTHY
    private String avgLatency;         // 120ms
    private Integer resourceUsageRate; // 42 (%)

    // AiPipeline 엔티티를 응답 DTO로 변환
    public static AiPipelineResponseDto fromEntity(AiPipeline entity) {
        return AiPipelineResponseDto.builder()
                .id(entity.getId())
                .engineName(entity.getEngineName())
                .status(entity.getStatus())
                .avgLatency(entity.getAvgLatency())
                .resourceUsageRate(entity.getResourceUsageRate())
                .build();
    }
}