package skincare.dto;

import lombok.Data;

@Data
public class AdminLoginRequestDto {
    private String adminId;
    private String password;
}