package skincare.dto.ai;

// AI 서버 피드백 적재 결과 DTO (POST /api/v1/ai/feedback 응답)
// AiFeedbackResponseDto 요청과 응답 데이터
public record AiFeedbackResponseDto(
        boolean queued, // 큐 등록 여부
        String queueId // 큐 ID
        ) {}
