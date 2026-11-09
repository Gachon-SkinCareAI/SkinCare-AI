package skincare.dto;

import lombok.Data;

@Data
// UserLoginRequestDto 요청과 응답 데이터
public class UserLoginRequestDto {
    private String userId;
    private String password;
}
