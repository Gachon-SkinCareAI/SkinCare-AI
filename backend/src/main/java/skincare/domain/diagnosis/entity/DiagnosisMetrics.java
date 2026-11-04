package skincare.domain.diagnosis.entity;

import jakarta.persistence.*;
import lombok.*;

// AI모델이 추출한 상처 영역 분석 원본 분석 데이터 엔티티
@Entity
@Table(name = "diagnosis_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiagnosisMetrics {

    // 진단 고유 ID (Diagnosis PK와 공유)
    @Id
    @Column(name = "diagnosis_id")
    private String diagnosisId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id")
    private Diagnosis diagnosis;

    // 상처 추정 면적
    @Column(name = "affected_area_size", length = 50)
    private String affectedAreaSize;

    // 세그멘테이션 IoU 교집합 평가 수치
    @Column(name = "segmentation_iou")
    private Double segmentationIou;

    // 상처 바운딩 박스 좌표값 (JSON 데이터 문자열 [x, y, w, h])
    @Column(name = "bounding_box", length = 100)
    private String boundingBox; // JSON string [x, y, w, h]

    // AI 추론 소요 시간 (ex. 120ms)
    @Column(name = "inference_time", length = 20)
    private String inferenceTime;
}