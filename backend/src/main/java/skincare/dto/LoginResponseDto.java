package skincare.dto;

// 로그인 처리 결과 DTO (발급 토큰 + 로그인 주체 정보)
// 컨트롤러에서 auth_token / user_info(또는 admin_info) 키로 나누어 응답
public record LoginResponseDto<T>(
        AuthTokenDto authToken, // 발급된 인증 토큰
        T profile               // 로그인한 사용자 또는 관리자 정보
) {
}
