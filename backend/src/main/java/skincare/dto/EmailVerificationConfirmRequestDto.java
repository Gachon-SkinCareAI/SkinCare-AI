package skincare.dto;

import lombok.Data;

// 이메일 인증 번호 검증 요청 DTO (POST /api/wc/auth/email/verify)
@Data
public class EmailVerificationConfirmRequestDto {
    private String email;    // 인증 대상 이메일
    private String authCode; // 이메일로 발송된 6자리 인증 번호
}