package skincare.dto.ai;

import java.util.List;
import java.util.Map;

// AI 서버 동적 문진 다음 질문 응답 DTO (POST /api/v1/ai/question 응답)
public record AiQuestionResponseDto(
        boolean done,               // 문진 종료 여부 (응급 규칙 충족 또는 필요한 슬롯 확보 시 true)
        String questionId,          // 다음 질문 ID (Question Bank 문항 ID)
        String question,            // 다음 질문 문구
        List<String> options,       // 선택형 질문 보기 (주관식이면 null)
        Map<String, Object> slots,  // 사용자 응답에서 추출된 슬롯을 반영한 최신 슬롯
        Map<String, String> optionValues, // 화면 문구에서 AI 선택지 값으로 변환
        boolean emergency           // 응급 규칙 충족 여부
) {
}
