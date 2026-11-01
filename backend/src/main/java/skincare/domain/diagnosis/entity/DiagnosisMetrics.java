package skincare.domain.diagnosis.entity;

import jakarta.persistence.*;
import lombok.*;

// AI 모델이 추출한 환부 탐지/분류 원본 지표 엔티티 (관리자 분석 상세 화면의 ai_raw_metrics)
// MVP는 Segmentation을 범위에서 제외하므로(설계서 1.5) 면적/IoU 대신 탐지 Confidence와 OOD Energy를 기록
@Entity
@Table(name = "diagnosis_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiagnosisMetrics {

    // 분석 고유 ID (Diagnosis PK와 공유)
    @Id
    @Column(name = "diagnosis_id")
    private String diagnosisId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id")
    private Diagnosis diagnosis;

    // 환부 바운딩 박스 좌표값 (JSON 데이터 문자열 [x, y, w, h])
    @Column(name = "bounding_box", length = 100)
    private String boundingBox; // JSON string [x, y, w, h]

    // YOLO 환부 탐지 Confidence (0.0 ~ 1.0)
    @Column(name = "detect_confidence")
    private Double detectConfidence;

    // OOD Energy 점수 (Gate 판단 근거)
    @Column(name = "energy_score")
    private Double energyScore;

    // AI 추론 소요 시간 (ex. 120ms)
    @Column(name = "inference_time", length = 20)
    private String inferenceTime;
}
