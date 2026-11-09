package skincare.domain.user.entity;

import jakarta.persistence.*;

import lombok.*;

import skincare.domain.global.BaseTimeEntity;

// [회원 변경 이력] 관리자의 회원 계정 상태/이용 플랜 변경 감사 이력 엔티티
// 변경자(관리자), 변경 일시(created_at), 변경 전/후 값, 사유를 함께 저장
@Entity
@Table(
        name = "user_change_histories",
        indexes = @Index(name = "idx_user_change_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
// UserChangeHistory 도메인 상태 관리
public class UserChangeHistory extends BaseTimeEntity {

    // 사용자 변경 이력 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 대상 회원 ID
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    // 변경 항목 (STATUS: 계정 상태, PLAN: 이용 플랜)
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    // 변경 전 값
    @Column(name = "previous_value", length = 30)
    private String previousValue;

    // 변경 후 값
    @Column(name = "new_value", nullable = false, length = 30)
    private String newValue;

    // 변경 사유
    @Column(length = 500)
    private String reason;

    // 변경 관리자 ID
    @Column(name = "changed_by", nullable = false, length = 36)
    private String changedBy;
}
