package skincare.dto;

import lombok.Data;

@Data
public class EmailVerificationConfirmRequestDto {
    private String email;
    private String authCode;
}