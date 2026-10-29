package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.inquiry.entity.InquiryAnswer;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class UserInquiryResponseDto {

    private String inquiryId;
    private InquiryType inquiryType;
    private String title;
    private String content;
    private InquiryStatus status;

    // 참소된 AI 상처 진단 기록 정보 (존재할 경우)
    private DiagnosisSummaryDto diagnosis;

    // 관리자 답변 내용 (답변 작성 시에만 포함)
    private String answerContent;
    private String answeredAt;

    private String createdAt;
    private String updatedAt;

    @Getter
    @Builder
    public static class DiagnosisSummaryDto {
        private String diagnosisId;
        private String suspectedDisease;   // 진단된 질환/상처명
        private String imageUrl;      // 진단에 사용된 이미지 URL
        private String createdAt;     // 진단 일시
    }

    public static UserInquiryResponseDto fromEntity(Inquiry inquiry) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        InquiryAnswer answer = inquiry.getAnswer();
        Diagnosis diagnosis = inquiry.getDiagnosis();

        DiagnosisSummaryDto diagnosisDto = null;
        if (diagnosis != null) {
            diagnosisDto = DiagnosisSummaryDto.builder()
                    .diagnosisId(diagnosis.getDiagnosisId())
                    .suspectedDisease(diagnosis.getSuspectedDisease())
                    .imageUrl(diagnosis.getImageUrl())
                    .createdAt(diagnosis.getCreatedAt() != null ? diagnosis.getCreatedAt().format(formatter) : null)
                    .build();
        }

        return UserInquiryResponseDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .inquiryType(inquiry.getInquiryType())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .diagnosis(diagnosisDto)
                .answerContent(answer != null ? answer.getAnswerContent() : null)
                .answeredAt(answer != null && answer.getCreatedAt() != null ? answer.getCreatedAt().format(formatter) : null)
                .createdAt(inquiry.getCreatedAt() != null ? inquiry.getCreatedAt().format(formatter) : null)
                .updatedAt(inquiry.getUpdatedAt() != null ? inquiry.getUpdatedAt().format(formatter) : null)
                .build();
    }
}