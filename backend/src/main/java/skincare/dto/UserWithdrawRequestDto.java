package skincare.dto;

import lombok.Data;

@Data
public class UserWithdrawRequestDto {
    private String password;
    private String reason;
}