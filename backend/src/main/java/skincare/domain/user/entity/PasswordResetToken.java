package skincare.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import skincare.domain.global.BaseTimeEntity;

import java.time.LocalDateTime;

// [비밀번호 재설정] 이메일 링크로 전달되는 일회성 비밀번호 재설정 토큰 엔티티
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PasswordResetToken extends BaseTimeEntity {

    // 재설정 토큰 고유 ID
    @Id
    @Column(name = "token_id", length = 36)
    private String tokenId; // UUID

    // 비밀번호 재설정 대상 사용자 이메일
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    // 토큰 만료 일시
    @Column(name = "expiration_time", nullable = false)
    private LocalDateTime expirationTime;

    // 토큰 사용 완료 여부
    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean used = false;

    // 토큰 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expirationTime);
    }

    // 토큰 사용 완료 처리
    public void useToken() {
        this.used = true;
    }
}
