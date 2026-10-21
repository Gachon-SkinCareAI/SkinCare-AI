package skincare.dto;

import lombok.Data;

@Data
public class UserSignUpRequestDto {
    private String userId;
    private String email;
    private String password;
    private String nickname;
}
