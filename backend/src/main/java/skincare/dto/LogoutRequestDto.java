package skincare.dto;

import lombok.Data;

// 사용자 로그아웃 요청 DTO (POST /api/wc/auth/logout)
@Data
public class LogoutRequestDto {
    private String refreshToken; // 폐기할 리프레시 토큰
}