package skincare.dto;

import lombok.Data;

@Data
// EmailVerificationConfirmRequestDto 요청과 응답 데이터
public class EmailVerificationConfirmRequestDto {
    private String email;
    private String authCode;
}
