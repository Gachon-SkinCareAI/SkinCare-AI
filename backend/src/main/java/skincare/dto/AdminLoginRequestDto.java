package skincare.dto;

import lombok.Data;

@Data
// AdminLoginRequestDto 요청과 응답 데이터
public class AdminLoginRequestDto {
    private String adminId;
    private String password;
}
