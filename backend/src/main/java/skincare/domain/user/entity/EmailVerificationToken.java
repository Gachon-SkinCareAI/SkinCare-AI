package skincare.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import skincare.domain.global.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailVerificationToken extends BaseTimeEntity {

    @Id
    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "auth_code", nullable = false, length = 6)
    private String authCode;

    @Column(name = "expiration_time", nullable = false)
    private LocalDateTime expirationTime;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    // 인증 번호 및 만료시간 갱신
    public void updateAuthCode(String authCode, LocalDateTime expirationTime) {
        this.authCode = authCode;
        this.expirationTime = expirationTime;
        this.verified = false;
    }

    // 인증 성공 완료 처리
    public void completeVerification() {
        this.verified = true;
    }

    // 인증 번호 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expirationTime);
    }
}