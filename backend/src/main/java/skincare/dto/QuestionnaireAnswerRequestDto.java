package skincare.dto;

import lombok.Data;

// 동적 문진 응답 제출 요청 DTO (POST /api/wc/diagnoses/{diagnosisId}/questionnaire)
// 첫 질문을 받을 때는 본문 없이(또는 빈 값으로) 요청하고, 이후 받은 질문의 question_id와 사용자 응답을 전송
@Data
public class QuestionnaireAnswerRequestDto {
    private String questionId; // 응답할 질문 ID (직전 응답에서 받은 next_question.question_id)
    private String answer;     // 사용자 응답 (선택형이면 보기 값, 주관식이면 입력 문장)
}
