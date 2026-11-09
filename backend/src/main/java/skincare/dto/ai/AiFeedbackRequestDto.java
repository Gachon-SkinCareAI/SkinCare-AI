package skincare.dto.ai;

// AI 서버 분석 결과 피드백 적재 요청 DTO (POST /api/v1/ai/feedback 요청)
// 파일 본문은 전송하지 않으며, training_use 동의 행만 이후 재학습 후보가 됨
// AiFeedbackRequestDto 요청과 응답 데이터
public record AiFeedbackRequestDto(
        String requestId, // 대상 predict 요청 ID
        String kind, // 피드백 종류 (ex. DISAGREE)
        String note, // 메모
        String photoRef // 앱 서버 사진 ID (분석 ID)
        ) {}
