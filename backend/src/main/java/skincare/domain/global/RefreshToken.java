package skincare.domain.global;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseTimeEntity {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "token_value", nullable = false, length = 500)
    private String tokenValue;

    public void updateToken(String newTokenValue) {
        this.tokenValue = newTokenValue;
    }
}