package skincare.dto;

import lombok.Data;

// 이메일 인증 번호 발송 요청 DTO (POST /api/wc/auth/email/send)
@Data
public class EmailVerificationRequestDto {
    private String email; // 인증 번호를 받을 이메일
}