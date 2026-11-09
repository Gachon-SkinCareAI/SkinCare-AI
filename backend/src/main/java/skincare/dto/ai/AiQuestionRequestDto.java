package skincare.dto.ai;

import java.util.Map;

// AI 서버 동적 문진 다음 질문 요청 DTO (POST /api/v1/ai/question 요청)
// AiQuestionRequestDto 요청과 응답 데이터
public record AiQuestionRequestDto(
        String requestId, // predict 요청 추적 ID
        String label, // predict 의심 유형
        Map<String, Object> slots, // 현재까지 확보된 문진 슬롯
        String questionId, // 직전 질문 ID (첫 질문 요청 시 null)
        String answer, // 직전 질문에 대한 사용자 응답 원문 (첫 질문 요청 시 null)
        int turn // 현재 문진 턴 (최대 10회, 설계서 6.2.4)
        ) {}
