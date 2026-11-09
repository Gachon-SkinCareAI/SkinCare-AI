package skincare.dto;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.inquiry.entity.InquiryAnswer;

import java.time.format.DateTimeFormatter;

// 문의 답변 응답 DTO (문의 목록/상세 응답의 answer 객체, 답변 등록 응답의 answer_info 객체)
@Getter
@Builder
// InquiryAnswerDto 요청과 응답 데이터
public class InquiryAnswerDto {
    private String answerId; // 답변 고유 ID
    private String adminId; // 답변 작성 관리자 ID
    private String content; // 답변 내용
    private String answeredAt; // 답변 등록 일시

    // InquiryAnswer 엔티티를 응답 DTO로 변환 (답변이 없으면 null)
    public static InquiryAnswerDto from(InquiryAnswer answer) {
        if (answer == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        return InquiryAnswerDto.builder()
                .answerId(answer.getAnswerId())
                .adminId(answer.getAnsweredBy())
                .content(answer.getAnswerContent())
                .answeredAt(
                        answer.getCreatedAt() != null
                                ? answer.getCreatedAt().format(formatter)
                                : null)
                .build();
    }
}
