package skincare.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import skincare.domain.global.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PasswordResetToken extends BaseTimeEntity {

    @Id
    @Column(name = "token_id", length = 36)
    private String tokenId; // UUID

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "expiration_time", nullable = false)
    private LocalDateTime expirationTime;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean used = false;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expirationTime);
    }

    public void useToken() {
        this.used = true;
    }
}
