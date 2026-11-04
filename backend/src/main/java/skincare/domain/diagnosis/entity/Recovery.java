package skincare.domain.diagnosis.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// [회복 경과] 특정 진단 로그에 대해 추가 등록하는 상처 추이 관찰 기록 엔티티
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
    private String recoveryId; // ex. REC-20261005-0104

    // 연관된 원본 진단로그
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private Diagnosis diagnosis;

    // 경과 촬영 이미지 URL
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    // 사용자 경과 메모
    @Column(columnDefinition = "TEXT")
    private String memo;

    // 자가 자각 통증 점수 (1 ~ 10)
    @Column(name = "pain_score")
    private Integer painScore;
}