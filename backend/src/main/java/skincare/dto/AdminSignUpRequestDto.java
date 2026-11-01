package skincare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 관리자 계정 생성 요청 DTO (POST /api/wc/admin/signup, 기존 관리자만 호출 가능)
@Data
public class AdminSignUpRequestDto {

    private String adminId;    // 관리자 아이디 (미입력 시 서버에서 ADM-... 형식으로 생성)

    @NotBlank(message = "관리자 이메일은 필수 입력 항목입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;      // 관리자 이메일 (로그인 ID)

    @NotBlank(message = "관리자 이름은 필수 입력 항목입니다.")
    private String name;       // 관리자 이름

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
    private String password;   // 관리자 비밀번호 (저장 시 암호화)

    private String department; // 소속 부서 (선택)
}
