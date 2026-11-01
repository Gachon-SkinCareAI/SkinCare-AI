package skincare.domain.global;

import jakarta.persistence.*;
import lombok.*;

// [리프레시 토큰] 사용자별 JWT Refresh Token 저장 엔티티
@Entity
@Table(name = "user_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseTimeEntity {

    // 토큰 소유자 ID (사용자/관리자 ID)
    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    // Refresh Token 값
    @Column(name = "token_value", nullable = false, length = 500)
    private String tokenValue;

    // Refresh Token 값 갱신 메서드
    public void updateToken(String newTokenValue) {
        this.tokenValue = newTokenValue;
    }
}