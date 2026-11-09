package skincare.dto;

import lombok.Data;

@Data
// UserSignUpRequestDto 요청과 응답 데이터
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
