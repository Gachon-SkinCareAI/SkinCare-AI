package skincare.dto;

import lombok.Data;

@Data
// UserProfileUpdateRequestDto 요청과 응답 데이터
public class UserProfileUpdateRequestDto {
    private String nickname;
    private String profileImageUrl;
}
