package skincare.domain.diagnosis.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

// AI 분석 의심 유형
// code: AI 서버 클래스명(snake_case), label: 서비스 화면 표시 문구
// UNKNOWN은 학습 클래스가 아닌 Confidence/OOD Gate 미통과 결과
@Getter
@RequiredArgsConstructor
public enum WoundType {
    ABRASION("abrasion", "찰과상 의심"),
    BRUISE("bruise", "타박상 의심"),
    BURN("burn", "화상 의심"),
    OPEN_WOUND("open_wound", "벌어진 상처 의심"),
    NORMAL_SKIN("normal_skin", "뚜렷한 손상 특징이 보이지 않음"),
    UNKNOWN("UNKNOWN", "판단 불가");

    private final String code;  // AI 서버 클래스명
    private final String label; // 화면 표시 문구

    // AI 서버 클래스명을 WoundType으로 변환 (알 수 없는 값은 강제로 분류하지 않고 UNKNOWN 처리)
    public static WoundType fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
