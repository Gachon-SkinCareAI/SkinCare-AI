package skincare.dto.ai;

import skincare.domain.diagnosis.entity.ENUM.RiskLevel;

import java.util.List;
import java.util.Map;

// AI 서버 공인 케어 카드 조회 요청 DTO (POST /api/v1/ai/guide 요청)
// AiGuideRequestDto 요청과 응답 데이터
public record AiGuideRequestDto(
        RiskLevel level, // analyze 결과 위험도
        List<String> ruleIds, // analyze 결과 규칙 ID
        Map<String, Object> slots, // 문진 슬롯
        String cardVersion // 케어 카드 버전 (null이면 AI 서버 기본 버전)
        ) {}
