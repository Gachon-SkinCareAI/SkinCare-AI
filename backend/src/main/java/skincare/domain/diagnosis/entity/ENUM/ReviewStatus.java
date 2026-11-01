package skincare.domain.diagnosis.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 관리자 분석 결과 검수 상태 (설계서 6.3.3 분석 로그 관리 - 상태 변경)
@Getter
@RequiredArgsConstructor
public enum ReviewStatus {
    UNREVIEWED("미검수"),
    NORMAL("정상"),
    NEEDS_REVIEW("확인 필요"),
    MISCLASSIFICATION_SUSPECTED("오분류 의심");

    private final String label; // 화면 표시용 한글 명칭
}
