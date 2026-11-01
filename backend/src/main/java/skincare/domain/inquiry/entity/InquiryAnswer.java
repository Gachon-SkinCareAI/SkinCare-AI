package skincare.domain.inquiry.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// [1:1 문의 답변] 사용자 문의에 대해 관리자가 작성한 답변 엔티티
@Entity
@Table(name = "inquiry_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InquiryAnswer extends BaseTimeEntity {

    // 답변 고유 ID
    @Id
    @Column(name = "answer_id", length = 36)
    private String answerId; // ex. ANS-20261005-0089

    // 답변 대상 1:1 문의
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private Inquiry inquiry;

    // 관리자 답변 본문 내용
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerContent;

    // 답변을 작성(최종 수정)한 관리자 ID
    @Column(name = "answered_by", length = 36)
    private String answeredBy;

    // 답변 내용 수정 (수정한 관리자로 작성자 갱신)
    public void updateAnswerContent(String answerContent, String adminId) {
        this.answerContent = answerContent;
        this.answeredBy = adminId;
    }
}
