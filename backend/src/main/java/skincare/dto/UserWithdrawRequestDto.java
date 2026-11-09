package skincare.dto;

import lombok.Data;

@Data
// UserWithdrawRequestDto 요청과 응답 데이터
public class UserWithdrawRequestDto {
    private String confirmation;
    private String password;
    private String reason;
}
