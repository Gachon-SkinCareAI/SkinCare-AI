package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.ai.entity.AiRetrainHistory;

import java.time.format.DateTimeFormatter;

// 관리자 MLOps 모델 재학습 이력 응답 DTO (GET /api/wc/admin/ai/retrain-history, 재학습 요청/잡 조회 응답)
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
// AiRetrainHistoryResponseDto 요청과 응답 데이터
public class AiRetrainHistoryResponseDto {

    private String historyId; // 재학습 잡 ID (AI 서버 job_id)
    private String executedAt; // 실행 일시
    private String triggerReason; // 트리거 사유 (manual, recall_drop, feedback_threshold)
    private String performanceChange; // 성능 변화 (ex. 96.2% (+2.1%))
    private String status; // 잡 상태 (queued, running, validating, awaiting_deploy, deploy_approved,
    // deployed, failed)
    private String message; // AI 서버 메세지 (실패 시 로그 요약)
    private String requestedBy; // 재학습 요청 관리자
    private String approvedBy; // 배포 승인 관리자
    private String approvedAt; // 배포 승인 일시

    // AiRetrainHistory 엔티티를 응답 DTO로 변환 (일시 문자열 포맷팅)
    public static AiRetrainHistoryResponseDto fromEntity(AiRetrainHistory entity) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return AiRetrainHistoryResponseDto.builder()
                .historyId(entity.getHistoryId())
                .executedAt(
                        entity.getExecutedAt() != null
                                ? entity.getExecutedAt().format(formatter)
                                : null)
                .triggerReason(entity.getTriggerReason())
                .performanceChange(entity.getPerformanceChange())
                .status(entity.getStatus())
                .message(entity.getMessage())
                .requestedBy(entity.getRequestedBy())
                .approvedBy(entity.getApprovedBy())
                .approvedAt(
                        entity.getApprovedAt() != null
                                ? entity.getApprovedAt().format(formatter)
                                : null)
                .build();
    }
}
