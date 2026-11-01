package skincare.dto;

import lombok.Builder;
import lombok.Getter;

// 로그인 성공 시 발급되는 인증 토큰 응답 DTO (로그인 응답의 auth_token 객체)
@Getter
@Builder
public class AuthTokenDto {

    @Builder.Default
    private String grantType = "Bearer"; // 토큰 타입 (Authorization: Bearer {access_token})
    private String accessToken;          // JWT 액세스 토큰
    private long expiresIn;              // 토큰 유효시간 (초)
}
