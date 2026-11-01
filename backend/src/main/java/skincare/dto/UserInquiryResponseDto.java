package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.domain.inquiry.entity.Inquiry;

import java.time.format.DateTimeFormatter;

// 사용자 1:1 문의 등록/목록/상세/수정 응답 DTO (/api/wc/inquiries)
@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class UserInquiryResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private String inquiryId;           // 문의 고유 ID
    private InquiryType inquiryType;    // 문의 유형 코드
    private String inquiryTypeLabel;    // 문의 유형 표시명
    private String title;               // 문의 제목
    private String content;             // 문의 내용
    private String createdAt;           // 문의 작성 일시
    private InquiryStatus status;       // 답변 상태 코드 (WAITING, COMPLETED)
    private String statusLabel;         // 답변 상태 표시명
    private RelatedDiagnosis relatedDiagnosis; // 연관 AI 분석 기록 (없으면 null)
    private InquiryAnswerDto answer;    // 관리자 답변 (답변 전이면 null)

    // 문의에 연동된 AI 분석 기록 요약 정보
    @Getter
    @Builder
    public static class RelatedDiagnosis {
        private String diagnosisId;      // 분석 고유 ID
        private String title;            // 분석 요약 (ex. 다리 · 화상 의심)
        private String imageUrl;         // 분석 이미지 조회 API 경로
        private String diagnosedAt;      // 분석 일시
        private RiskLevel riskLevel;     // 위험도
    }

    // Inquiry 엔티티(연관 분석, 답변 포함)를 사용자 응답 DTO로 변환
    public static UserInquiryResponseDto fromEntity(Inquiry inquiry) {
        return UserInquiryResponseDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .inquiryType(inquiry.getInquiryType())
                .inquiryTypeLabel(inquiry.getInquiryType().getLabel())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .createdAt(inquiry.getCreatedAt() != null ? inquiry.getCreatedAt().format(FORMATTER) : null)
                .status(inquiry.getStatus())
                .statusLabel(inquiry.getStatus().getLabel())
                .relatedDiagnosis(toRelatedDiagnosis(inquiry.getDiagnosis()))
                .answer(InquiryAnswerDto.from(inquiry.getAnswer()))
                .build();
    }

    // 연관 분석 기록 요약 변환
    private static RelatedDiagnosis toRelatedDiagnosis(Diagnosis diagnosis) {
        if (diagnosis == null) {
            return null;
        }
        String bodyPart = diagnosis.getBodyPart() != null ? diagnosis.getBodyPart().getLabel() : "";
        return RelatedDiagnosis.builder()
                .diagnosisId(diagnosis.getDiagnosisId())
                .title(bodyPart + " · " + diagnosis.getSuspectedDisease())
                .imageUrl(DiagnosisResponseDto.imagePath(diagnosis.getDiagnosisId()))
                .diagnosedAt(diagnosis.getCreatedAt() != null ? diagnosis.getCreatedAt().format(FORMATTER) : null)
                .riskLevel(diagnosis.getRiskLevel())
                .build();
    }
}
