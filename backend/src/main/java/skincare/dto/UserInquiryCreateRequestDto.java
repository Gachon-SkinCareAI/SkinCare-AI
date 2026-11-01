package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import skincare.domain.inquiry.entity.ENUM.InquiryType;

// 사용자 1:1 문의 등록 요청 DTO (POST /api/wc/inquiries)
@Getter
@Setter
@NoArgsConstructor
public class UserInquiryCreateRequestDto {

    @NotNull(message = "문의 유형은 필수 선택 항목입니다.")
    private InquiryType inquiryType; // AI_RESULT, ACCURACY, SYSTEM_ERROR, OTHER

    @NotBlank(message = "문의 제목은 필수 입력 항목입니다.")
    private String title;   // 문의 제목

    @NotBlank(message = "문의 내용은 필수 입력 항목입니다.")
    private String content; // 문의 내용

    // AI 상처 진단 기록 연동 (선택 사항)
    private String diagnosisId;
}