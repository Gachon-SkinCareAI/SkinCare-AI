package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.ai.entity.AiRetrainHistory;

import java.time.format.DateTimeFormatter;

// 관리자 MLOps 모델 재학습 이력 조회 응답 DTO (GET /api/wc/admin/ai/retrain-history)
@Getter
@Builder
public class AiRetrainHistoryResponseDto {

    private String historyId;          // ML-20260915-01
    private String executedAt;         // 2026-09-15 14:30:00
    private String triggerReason;      // 데이터 드리프트 감지 및 정기 재학습
    private String performanceChange;  // Accuracy +1.2%, Recall +0.8%
    private String status;             // COMPLETED, FAILED

    // AiRetrainHistory 엔티티를 응답 DTO로 변환 (실행일시 문자열 포맷팅)
    public static AiRetrainHistoryResponseDto fromEntity(AiRetrainHistory entity) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return AiRetrainHistoryResponseDto.builder()
                .historyId(entity.getHistoryId())
                .executedAt(entity.getExecutedAt() != null ? entity.getExecutedAt().format(formatter) : null)
                .triggerReason(entity.getTriggerReason())
                .performanceChange(entity.getPerformanceChange())
                .status(entity.getStatus())
                .build();
    }
}