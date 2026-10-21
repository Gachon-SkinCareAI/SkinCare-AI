package skincare.domain.ai.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// [AI 모델 성능 평가 지표] 대시보드 노출용 비전 모델 성능 스탯 데이터 엔티티
@Entity
@Table(name = "ai_model_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiModelMetrics extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 지표 식별 키
    @Column(name = "metric_key", nullable = false, length = 50)
    private String metricKey; // classification_accuracy, emergency_recall 등

    // 지표 수치 (ex. 94.2)
    @Column(nullable = false)
    private Double metricValue;

    // 단위 (ex. %)
    @Column(length = 20)
    private String unit;

    // 전월/전주 대비 증감치 (ex. +0.8%)
    @Column(name = "change_value", length = 20)
    private String changeValue;

    // 지표 항목 설명
    @Column(length = 255)
    private String description;
}