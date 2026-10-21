package skincare.domain.ai.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// [AI 파이프라인] 비전 추론 모델 파이프라인 가동 및 벤치마크 지표 엔티티
@Entity
@Table(name = "ai_pipelines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiPipeline extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 모델 엔진 명칭
    @Column(name = "engine_name", nullable = false, length = 100)
    private String engineName;

    // 파이프라인 상태 (HEALTHY: 정상, UNHEALTHY: 이상 발생)
    @Column(nullable = false, length = 20)
    private String status; // HEALTHY, UNHEALTHY

    // 평균 처리 소요시간 (ex. 120ms)
    @Column(name = "avg_latency", nullable = false, length = 20)
    private String avgLatency;

    // 리소스 점유율 (ex. 42 %)
    @Column(name = "resource_usage_rate", nullable = false)
    private Integer resourceUsageRate;
}