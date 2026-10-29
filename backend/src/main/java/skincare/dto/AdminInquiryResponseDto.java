package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.inquiry.entity.InquiryAnswer;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class AdminInquiryResponseDto {

    private String inquiryId;
    private String userId;
    private String userEmail;
    private String userName;
    private String diagnosisId; // 연관된 진단 기록 ID (선택)
    private InquiryType inquiryType;
    private String title;
    private String content;
    private InquiryStatus status;

    // 답변 정보 (InquiryAnswer 엔티티 매핑)
    private String answerId;
    private String answerContent;
    private String answeredAt;

    private String createdAt;
    private String updatedAt;

    public static AdminInquiryResponseDto fromEntity(Inquiry inquiry) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        InquiryAnswer answer = inquiry.getAnswer();

        return AdminInquiryResponseDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .userId(inquiry.getUser() != null ? inquiry.getUser().getUserId() : null)
                .userEmail(inquiry.getUser() != null ? inquiry.getUser().getEmail() : null)
                .userName(inquiry.getUser() != null ? inquiry.getUser().getNickname() : null)
                .diagnosisId(inquiry.getDiagnosis() != null ? inquiry.getDiagnosis().getDiagnosisId() : null)
                .inquiryType(inquiry.getInquiryType())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .answerId(answer != null ? answer.getAnswerId() : null)
                .answerContent(answer != null ? answer.getAnswerContent() : null)
                .answeredAt(answer != null && answer.getCreatedAt() != null ? answer.getCreatedAt().format(formatter) : null)
                .createdAt(inquiry.getCreatedAt() != null ? inquiry.getCreatedAt().format(formatter) : null)
                .updatedAt(inquiry.getUpdatedAt() != null ? inquiry.getUpdatedAt().format(formatter) : null)
                .build();
    }
}