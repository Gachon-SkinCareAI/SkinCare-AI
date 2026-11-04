package skincare.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequestDto {
    private String nickname;
    private String profileImageUrl;
}