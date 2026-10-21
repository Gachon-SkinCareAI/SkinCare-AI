package skincare.domain.user.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 사용자 설정 엔티티
// 푸시알림, 환경설정 관련
@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserSettings extends BaseTimeEntity {

    // 사용자 고유 ID(User PK와 공유)
    @Id
    @Column(name = "user_id")
    private String userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // AI 진단 완료 푸시 알림 동의 여부
    @Column(name = "push_diagnosis_alert", nullable = false)
    @Builder.Default
    private boolean pushDiagnosisAlert = true;

    // 상처 회복 경과 리마인더 알림 동의 여부
    @Column(name = "push_recovery_reminder", nullable = false)
    @Builder.Default
    private boolean pushRecoveryReminder = true;

    // 환경설정 변경 메서드
    public void updateSettings(Boolean pushDiagnosisAlert, Boolean pushRecoveryReminder) {
        if (pushDiagnosisAlert != null) this.pushDiagnosisAlert = pushDiagnosisAlert;
        if (pushRecoveryReminder != null) this.pushRecoveryReminder = pushRecoveryReminder;
    }
}