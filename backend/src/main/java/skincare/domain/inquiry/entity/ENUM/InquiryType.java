package skincare.domain.inquiry.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 문의 유형 (AI_RESULT: AI 분석 결과, ACCURACY: 분석 정확도, SYSTEM_ERROR: 시스템 오류, OTHER: 기타)
@Getter
@RequiredArgsConstructor
public enum InquiryType {
    AI_RESULT("AI 분석 결과 문의"),
    ACCURACY("AI 분석 정확도 문의"),
    SYSTEM_ERROR("시스템 오류 문의"),
    OTHER("기타 문의");

    private final String label; // 화면 표시용 한글 명칭
}
