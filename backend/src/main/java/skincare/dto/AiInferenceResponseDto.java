package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;

@Getter
@Builder
public class AiInferenceResponseDto {

    private String suspectedDisease;  // AI 예측 의심 질환명
    private RiskLevel riskLevel;       // NORMAL, OBSERVATION, WARNING, EMERGENCY
    private Double confidenceScore;    // AI 예측 신뢰도 점수
    private BodyPart bodyPart;          // 감지된 신체 부위
    private String firstAidGuide;     // 응급처치 가이드
    private String precautionsGuide;  // 주의사항 및 행동 지침

    // DiagnosisMetrics 매핑용 필드
    private String affectedAreaSize;   // 상처 추정 면적 (ex. "12.5 cm²")
    private Double segmentationIou;    // 세그멘테이션 IoU 수치 (ex. 0.88)
    private String boundingBox;        // JSON string [x, y, w, h] (ex. "[120, 85, 230, 190]")
    private String inferenceTime;      // AI 추론 소요 시간 (ex. "115ms")
}