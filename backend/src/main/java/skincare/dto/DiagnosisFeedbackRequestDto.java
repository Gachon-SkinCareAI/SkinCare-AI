package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// AI 분석 결과 피드백 요청 DTO (POST /api/wc/diagnoses/{diagnosisId}/feedback)
// 피드백은 AI 서버 큐에 적재되며, 학습 활용에 동의한 분석만 이후 재학습 후보가 됨
@Data
public class DiagnosisFeedbackRequestDto {

    @NotBlank(message = "피드백 종류는 필수 입력 항목입니다.")
    @Pattern(regexp = "AGREE|DISAGREE|OTHER", message = "피드백 종류는 AGREE, DISAGREE, OTHER 중 하나여야 합니다.")
    private String kind; // AGREE: 결과 동의, DISAGREE: 결과 불일치, OTHER: 기타

    @Size(max = 500, message = "피드백 메모는 500자 이하로 입력해 주세요.")
    private String note; // 피드백 메모 (선택)
}
