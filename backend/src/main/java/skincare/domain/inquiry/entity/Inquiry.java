package skincare.domain.inquiry.entity;

import jakarta.persistence.*;

import lombok.*;

import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.global.BaseTimeEntity;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.domain.user.entity.User;

// [1:1 문의] 사용자가 앱에서 작성한 고객 문의 엔티티
@Entity
@Table(
        name = "inquiries",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_inquiry_user_request",
                        columnNames = {"user_id", "client_request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
// Inquiry 도메인 상태 관리
public class Inquiry extends BaseTimeEntity {

    // 문의 고유 ID ex. INQ-20261005-0014
    @Id
    @Column(name = "inquiry_id", length = 36)
    private String inquiryId;

    @Column(name = "client_request_id", length = 36)
    private String clientRequestId;

    // Optional reference snapshot; deleting a photo must not be blocked by a support request.
    @Column(name = "analysis_id", length = 36)
    private String analysisId;

    @Version
    @Column(nullable = false)
    private long version;

    // 문의를 작성한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 문 의에 연동된 상처 진단 기록 (선택 사항)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id")
    private Diagnosis diagnosis;

    // 문의 유형 (AI_RESULT: AI분석, ACCURACY: 정확도, SYSTEM_ERROR: 오류, OTHER: 기타)
    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false, length = 30)
    private InquiryType inquiryType;

    // 문의 제목
    @Column(nullable = false, length = 200)
    private String title;

    // 문의 내용
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 답변 상태 (WAITING: 답변대기, COMPLETED: 답변완료)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InquiryStatus status = InquiryStatus.WAITING;

    // 관리자 답변 정보 (1:1 연관관계)
    @OneToOne(mappedBy = "inquiry", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private InquiryAnswer answer;

    // 답변 완료 상태 변경 메서드
    public void completeAnswer() {
        this.status = InquiryStatus.COMPLETED;
    }

    public void completeAnswer(InquiryAnswer value) {
        this.answer = value;
        completeAnswer();
    }

    public void resetAnswerStatus() {
        this.answer = null;
        this.status = InquiryStatus.WAITING;
    }

    public void updateInquiry(InquiryType type, String title, String content, Diagnosis diagnosis) {
        this.inquiryType = type;
        this.title = title;
        this.content = content;
        this.diagnosis = diagnosis;
    }
}
