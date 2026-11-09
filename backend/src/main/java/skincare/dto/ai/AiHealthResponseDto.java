package skincare.dto.ai;

import java.util.Map;

// AI 서버 상태 확인 결과 DTO (GET /api/v1/ai/health 응답)
// 모델이 없으면 status=degraded 이며 이때 /ai/predict는 503 반환
// AiHealthResponseDto 요청과 응답 데이터
public record AiHealthResponseDto(
        String status, // ok, degraded, starting (AI 서버 연결 실패 시 메인 서버가 unreachable로 설정)
        Map<String, String> models, // 모델 버전 (detect, classify)
        String bankVersion, // 문항 뱅크 버전
        String cardVersion, // 케어 카드 버전
        Integer inflight, // 진행 중 추론 수
        Integer maxInflight // 최대 동시 추론 수 (기본 2)
        ) {

    // AI 서버에 연결할 수 없을 때 대시보드 표시용 상태 객체
    public static AiHealthResponseDto unreachable() {
        return new AiHealthResponseDto("unreachable", Map.of(), null, null, null, null);
    }
}
