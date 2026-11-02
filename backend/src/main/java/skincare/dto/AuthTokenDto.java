package skincare.dto;

import lombok.Builder;
import lombok.Getter;

// 로그인/토큰 재발급 시 발급되는 인증 토큰 응답 DTO (로그인 응답의 auth_token 객체)
@Getter
@Builder
public class AuthTokenDto {

    @Builder.Default
    private String grantType = "Bearer"; // 토큰 타입 (Authorization: Bearer {access_token})
    private String accessToken;          // JWT 액세스 토큰
    private long expiresIn;              // 액세스 토큰 유효시간 (초)
    private String refreshToken;         // 리프레시 토큰 (액세스 토큰 만료 시 재발급 요청에 사용)
    private long refreshExpiresIn;       // 리프레시 토큰 유효시간 (초)
}
