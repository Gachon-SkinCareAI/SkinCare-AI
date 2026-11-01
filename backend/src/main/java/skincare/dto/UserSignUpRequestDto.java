package skincare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 사용자 회원가입 요청 DTO (POST /api/wc/auth/signup)
// 설계서 기준 회원 아이디는 서버에서 생성하며, 이메일 인증 및 약관 동의가 필요
@Data
public class UserSignUpRequestDto {

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;    // 이메일 (로그인 ID, 사전 이메일 인증 필요)

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
    private String password; // 비밀번호 (저장 시 암호화)

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    @Size(max = 50, message = "닉네임은 50자 이하로 입력해 주세요.")
    private String nickname; // 닉네임

    private boolean agreedTerms;   // 서비스 이용약관 동의 여부 (필수 동의)
    private boolean agreedPrivacy; // 개인정보 처리방침 동의 여부 (필수 동의)
}
