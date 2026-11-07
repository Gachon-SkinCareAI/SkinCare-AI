package skincare.domain.inquiry.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 문의 답변 상태 (WAITING: 답변대기, COMPLETED: 답변완료)
@Getter
@RequiredArgsConstructor
public enum InquiryStatus {
    WAITING("답변대기"),
    COMPLETED("답변완료");

    private final String label; // 화면 표시용 한글 명칭
}
