package skincare.dto;

import lombok.Data;

@Data
// PasswordResetRequestDto 요청과 응답 데이터
public class PasswordResetRequestDto {
    private String email;
}
