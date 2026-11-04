package skincare.dto;

import lombok.Data;

@Data
public class PasswordResetConfirmRequestDto {
    private String resetToken;    // 이메일 링크를 통해 전달받은 일회성 토큰
    private String newPassword;   // 변경할 새 비밀번호
}