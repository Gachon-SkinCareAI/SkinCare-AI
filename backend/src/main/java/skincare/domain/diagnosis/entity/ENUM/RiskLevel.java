package skincare.domain.diagnosis.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// AI 분석 위험도 단계
// OBSERVATION: 관찰, WARNING: 경고, EMERGENCY: 응급, UNKNOWN: 판단 불가 (AI Gate 미통과)
@Getter
@RequiredArgsConstructor
public enum RiskLevel {
    OBSERVATION("관찰"),
    WARNING("경고"),
    EMERGENCY("응급"),
    UNKNOWN("판단 불가");

    private final String label; // 화면 표시용 한글 명칭
}
