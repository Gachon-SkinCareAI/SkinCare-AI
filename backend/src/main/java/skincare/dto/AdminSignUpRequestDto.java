package skincare.dto;

import lombok.Data;

@Data
// AdminSignUpRequestDto 요청과 응답 데이터
public class AdminSignUpRequestDto {
    private String adminId;
    private String name;
    private String password;
}
