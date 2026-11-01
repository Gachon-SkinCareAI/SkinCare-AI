package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 관리자 문의 답변 등록/수정 요청 DTO (POST, PUT /api/wc/admin/inquiries/{inquiryId}/answer)
@Getter
@Setter
@NoArgsConstructor
public class AdminInquiryAnswerRequestDto {

    @NotBlank(message = "답변 내용은 필수 입력 항목입니다.")
    private String answerContent; // 관리자 답변 본문 내용
}