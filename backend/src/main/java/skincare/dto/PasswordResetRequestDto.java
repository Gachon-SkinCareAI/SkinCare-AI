package skincare.dto;

import lombok.Data;

// 비밀번호 재설정 메일 발송 요청 DTO (POST /api/wc/auth/password/reset)
@Data
public class PasswordResetRequestDto {
    private String email; // 재설정 링크를 받을 가입 이메일
}