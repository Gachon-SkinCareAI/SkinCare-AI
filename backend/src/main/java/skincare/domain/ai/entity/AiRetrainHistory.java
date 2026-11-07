package skincare.domain.ai.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [AI 재학습 이력] MLOps 비전 모델 재학습 및 배포 수행 이력 엔티티
// 상태 흐름: queued → running → validating → awaiting_deploy → (관리자 승인) deploy_approved → deployed / 실패 시 failed
@Entity
@Table(name = "ai_retrain_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiRetrainHistory extends BaseTimeEntity {

    public static final String STATUS_AWAITING_DEPLOY = "awaiting_deploy"; // 검증 통과, 배포 승인 대기
    public static final String STATUS_DEPLOY_APPROVED = "deploy_approved"; // 관리자 배포 승인 완료

    // 재학습 작업 고유 ID (AI 서버 job_id)
    @Id
    @Column(name = "history_id", length = 36)
    private String historyId;

    // 재학습 실행 일시
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    // 재학습 트리거 사유 (manual, recall_drop, feedback_threshold)
    @Column(name = "trigger_reason", nullable = false, length = 100)
    private String triggerReason;

    // 학습 후 성능 변화 (ex. 96.2% (+2.1%), 학습 완료 전에는 '측정 전')
    @Column(name = "performance_change", nullable = false, length = 50)
    private String performanceChange;

    // 재학습 잡 상태 (queued, running, validating, awaiting_deploy, deploy_approved, deployed, failed)
    @Column(nullable = false, length = 20)
    private String status;

    // AI 서버 메세지 (실패 시 로그 요약)
    @Column(length = 500)
    private String message;

    // 재학습 요청 관리자 ID
    @Column(name = "requested_by", length = 36)
    private String requestedBy;

    // 배포 승인 관리자 ID
    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    // 배포 승인 일시
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // AI 서버 잡 상태 동기화 (관리자가 승인한 상태는 AI 서버가 awaiting_deploy를 유지하더라도 덮어쓰지 않음)
    public void syncStatus(String status, String message) {
        if (status == null) return;
        if (STATUS_DEPLOY_APPROVED.equals(this.status) && STATUS_AWAITING_DEPLOY.equals(status)) return;
        this.status = status;
        if (message != null) {
            this.message = message.length() > 500 ? message.substring(0, 500) : message;
        }
    }

    // 관리자 배포 승인 처리 (사람 승인 없이 모델 가중치를 교체하지 않음, 설계서 1.5)
    public void approveDeploy(String adminId) {
        this.status = STATUS_DEPLOY_APPROVED;
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
    }
}
