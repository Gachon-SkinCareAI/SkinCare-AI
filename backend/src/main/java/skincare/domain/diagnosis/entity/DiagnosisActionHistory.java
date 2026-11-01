package skincare.domain.diagnosis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.ReviewStatus;
import skincare.domain.global.BaseTimeEntity;

// [분석 처리 이력] 관리자의 분석건 처리 상태/검수 변경 감사 이력 엔티티 (설계서 6.3.3)
// 변경 관리자와 처리 시각(created_at)을 함께 저장하여 누가 언제 무엇을 변경했는지 추적
@Entity
@Table(name = "diagnosis_action_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiagnosisActionHistory extends BaseTimeEntity {

    // 이력 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 대상 분석 기록
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private Diagnosis diagnosis;

    // 변경 전 처리 상태
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "previous_status", length = 20)
    private DiagnosisStatus previousStatus;

    // 변경 후 처리 상태
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "current_status", length = 20)
    private DiagnosisStatus currentStatus;

    // 변경 후 검수 상태
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "review_status", length = 30)
    private ReviewStatus reviewStatus;

    // 관리자 의견/조치 내용
    @Column(columnDefinition = "TEXT")
    private String note;

    // 처리 관리자 ID
    @Column(name = "processed_by", nullable = false, length = 36)
    private String processedBy;
}
