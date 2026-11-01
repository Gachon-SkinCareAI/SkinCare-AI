package skincare.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import skincare.domain.global.BaseTimeEntity;

import java.time.LocalDateTime;

// [이메일 인증] 회원가입 전 이메일 인증 번호 발급 및 검증 상태 관리 엔티티
@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailVerificationToken extends BaseTimeEntity {

    // 인증 대상 이메일 (PK)
    @Id
    @Column(name = "email", length = 100)
    private String email;

    // 6자리 인증 번호
    @Column(name = "auth_code", nullable = false, length = 6)
    private String authCode;

    // 인증 번호 만료 일시
    @Column(name = "expiration_time", nullable = false)
    private LocalDateTime expirationTime;

    // 인증 완료 여부
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