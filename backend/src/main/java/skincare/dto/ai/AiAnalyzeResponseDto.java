package skincare.dto.ai;

import skincare.domain.diagnosis.entity.ENUM.RiskLevel;

import java.util.List;

// AI 서버 위험도 평가 결과 DTO (POST /api/v1/ai/analyze 응답)
// ex. { "level": "WARNING", "rule_ids": ["open.dirty"], "missing_slots": [], "scenario_source": "user" }
public record AiAnalyzeResponseDto(
        RiskLevel level,             // 위험도 (OBSERVATION, WARNING, EMERGENCY, UNKNOWN)
        List<String> ruleIds,        // 적용된 위험도 규칙 ID
        List<String> missingSlots,   // 확보되지 않은 문진 슬롯
        String scenarioSource        // 시나리오 판단 근거 (ex. user)
) {
}
