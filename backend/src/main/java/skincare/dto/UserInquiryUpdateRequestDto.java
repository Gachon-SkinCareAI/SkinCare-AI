package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import skincare.domain.inquiry.entity.ENUM.InquiryType;

@Getter
@Setter
@NoArgsConstructor
public class UserInquiryUpdateRequestDto {

    @NotNull(message = "문의 유형은 필수 선택 항목입니다.")
    private InquiryType inquiryType;

    @NotBlank(message = "문의 제목은 필수 입력 항목입니다.")
    private String title;

    @NotBlank(message = "문의 내용은 필수 입력 항목입니다.")
    private String content;

    // 변경할 AI 진단 기록 ID (선택 사항)
    private Long diagnosisId;
}