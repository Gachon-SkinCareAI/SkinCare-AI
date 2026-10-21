package skincare.domain.ai.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [AI 재학습 이력] MLOps 비전 모델 재학습 및 배포 수행 이력 엔티티
@Entity
@Table(name = "ai_retrain_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiRetrainHistory extends BaseTimeEntity {

    // 재학습 작업 고유 ID
    @Id
    @Column(name = "history_id", length = 36)
    private String historyId; // ex. ML-20260915-01

    // 재학습 실행 일시
    @Column(name = "executed_at", nullable = false, length = 20)
    private LocalDateTime executedAt;

    // 재학습 트리거 사유
    @Column(name = "trigger_reason", nullable = false, length = 100)
    private String triggerReason;

    // 학습 후 성능 변화
    @Column(name = "performance_change", nullable = false, length = 50)
    private String performanceChange;

    // 배포/재학습 결과 상태 (COMPLETED, FAILED 등)
    @Column(nullable = false, length = 20)
    private String status;
}