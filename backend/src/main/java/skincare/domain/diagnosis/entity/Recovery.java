package skincare.domain.diagnosis.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// [회복 경과] 특정 분석 기록에 대해 추가 등록하는 상처 추이 관찰 기록 엔티티
// 같은 분석 기록(diagnosis)에 연결된 회복 기록들이 하나의 상처 경과 타임라인을 구성
@Entity
@Table(name = "recoveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Recovery extends BaseTimeEntity {

    // 회복 경과 기록 고유 ID
    @Id
    @Column(name = "recovery_id", length = 36)
    private String recoveryId; // ex. REC-20261101-153022-3fa85f6457a1

    // 연관된 원본 분석 기록
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private Diagnosis diagnosis;

    // 경과 촬영 이미지 저장 경로 (파일 저장소 storage key)
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    // 사용자 경과 메모
    @Column(columnDefinition = "TEXT")
    private String memo;

    // 자가 자각 통증 점수 (0 ~ 10)
    @Column(name = "pain_score")
    private Integer painScore;

    // 최초 분석 사진 대비 환부 박스 면적 비율 차 (AI /tracking 참고값, 비교 불가 시 null)
    @Column(name = "area_ratio_delta")
    private Double areaRatioDelta;

    // 면적 변화 경향 (smaller, similar, larger, uncomparable / AI 서버 미연결 시 null)
    // 임상적 호전 판단이 아닌 촬영 사진 기준 참고값 (설계서 7.4 tracking)
    @Column(name = "trend_hint", length = 20)
    private String trendHint;

    // 비교 결과 안내 문구 (촬영 조건 보정이 없다는 안내 등)
    @Column(name = "tracking_note", length = 255)
    private String trackingNote;
}
