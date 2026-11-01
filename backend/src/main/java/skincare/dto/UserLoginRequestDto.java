package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 사용자 로그인 요청 DTO (POST /api/wc/auth/login)
@Data
public class UserLoginRequestDto {

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    private String email;    // 이메일 (로그인 ID)

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    private String password; // 비밀번호
}
