package skincare.dto;

import lombok.Data;

@Data
// LogoutRequestDto 요청과 응답 데이터
public class LogoutRequestDto {
    private String refreshToken;
}
