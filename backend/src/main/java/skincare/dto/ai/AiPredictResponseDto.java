package skincare.dto.ai;

import java.util.List;
import java.util.Map;

// AI 서버 이미지 분석 결과 DTO (POST /api/v1/ai/predict 응답)
//   AI 서버 명세 확정 시 이 DTO의 필드명만 맞추면 됨 (JSON은 snake_case로 자동 변환)
// AiPredictResponseDto 요청과 응답 데이터
public record AiPredictResponseDto(
        String requestId, // 분석 요청 추적 ID (feedback 등 후속 요청에 사용)
        String label, // 의심 유형 (abrasion, bruise, burn, open_wound, normal_skin, UNKNOWN)
        Double confidence, // 분류 Top-1 Confidence (0.0 ~ 1.0)
        Boolean gatePassed, // Confidence/OOD Gate 통과 여부 (false면 label=UNKNOWN)
        String reason, // Gate 미통과 사유 (ex. low_conf, low_margin, ood, no_roi)
        List<Integer> bbox, // 환부 Bounding Box [x, y, w, h]
        Double detectConfidence, // YOLO 환부 탐지 Confidence
        Double energyScore, // OOD Energy 점수
        Map<String, String> modelVersion, // 사용 모델 버전 (detect, classify)
        Long inferenceMs // 추론 소요 시간 (ms)
        ) {}
