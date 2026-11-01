package skincare.dto.ai;

import java.util.Map;

// AI 서버 위험도 평가 요청 DTO (POST /api/v1/ai/analyze 요청)
// 이미지를 다시 추론하지 않고 predict 결과와 문진 슬롯으로 규칙 엔진 평가를 요청
public record AiAnalyzeRequestDto(
        String requestId,          // predict 요청 추적 ID
        String label,              // predict 의심 유형
        Double confidence,         // predict Confidence
        Boolean gatePassed,        // Gate 통과 여부 (false이고 응급 규칙이 아니면 level=UNKNOWN)
        Map<String, Object> slots  // 문진 슬롯 (발생 시점, 출혈, 수포, 통증 등)
) {
}
