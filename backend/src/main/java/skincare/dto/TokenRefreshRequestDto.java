package skincare.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

// 토큰 재발급 요청 DTO (POST /api/wc/auth/token/refresh, /api/wc/admin/auth/token/refresh)
@Data
// TokenRefreshRequestDto 요청과 응답 데이터
public class TokenRefreshRequestDto {

    @NotBlank(message = "refresh_token은 필수 입력 항목입니다.")
    private String refreshToken; // 로그인 시 발급받은 리프레시 토큰
}
