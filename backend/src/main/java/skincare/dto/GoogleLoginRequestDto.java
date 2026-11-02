package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 모바일 Google 소셜 로그인 요청 DTO (POST /api/wc/auth/social/google)
@Data
public class GoogleLoginRequestDto {

    @NotBlank(message = "id_token은 필수 입력 항목입니다.")
    private String idToken; // Google Sign-In SDK에서 발급받은 ID Token
}
