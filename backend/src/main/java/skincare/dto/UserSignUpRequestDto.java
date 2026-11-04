package skincare.dto;

import lombok.Data;

@Data
public class UserSignUpRequestDto {
    private String userId;
    private String email;
    private String password;
    private String nickname;
    private boolean agreedTerms;
    private boolean agreedPrivacy;
    private String termsVersion;
    private String privacyVersion;
}
