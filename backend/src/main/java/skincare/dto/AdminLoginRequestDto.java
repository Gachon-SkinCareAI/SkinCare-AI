package skincare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 관리자 로그인 요청 DTO (POST /api/wc/admin/auth/login)
@Data
public class AdminLoginRequestDto {

    // 관리자 이메일 (이메일 미등록 기존 관리자는 관리자 아이디로도 로그인 가능)
    @NotBlank(message = "관리자 이메일은 필수 입력 항목입니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    private String password; // 관리자 비밀번호
}
