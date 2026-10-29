package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminInquiryAnswerRequestDto {

    @NotBlank(message = "답변 내용은 필수 입력 항목입니다.")
    private String answerContent;
}