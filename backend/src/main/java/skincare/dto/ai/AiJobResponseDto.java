package skincare.dto.ai;

// AI 서버 재학습 잡 정보 DTO (POST /api/v1/ai/retrain, GET /api/v1/ai/jobs/{job_id} 응답)
// 상태 흐름: queued → running → validating → awaiting_deploy → (관리자 승인) deployed / 실패 시 failed
// AiJobResponseDto 요청과 응답 데이터
public record AiJobResponseDto(
        String jobId, // 잡 ID (이미 진행 중이면 기존 ID)
        String status, // 잡 상태
        String message // 실패 시 로그 요약
        ) {}
