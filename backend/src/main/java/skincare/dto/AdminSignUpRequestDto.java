package skincare.dto;

import lombok.Data;

@Data
public class AdminSignUpRequestDto {
    private String adminId;
    private String name;
    private String password;
}