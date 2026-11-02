package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

// 관리자 MLOps 재학습 요청 DTO (POST /api/wc/admin/ai/retrain)
@Data
public class AiRetrainRequestDto {

    // 재학습 사유 (manual: 수동, recall_drop: 주요 위험 유형 Recall 하락, feedback_threshold: 피드백 누적)
    @NotBlank(message = "재학습 사유는 필수 입력 항목입니다.")
    @Pattern(regexp = "manual|recall_drop|feedback_threshold", message = "reason은 manual, recall_drop, feedback_threshold 중 하나여야 합니다.")
    private String reason;
}
