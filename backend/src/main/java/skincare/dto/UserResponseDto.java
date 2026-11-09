package skincare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
// UserResponseDto 요청과 응답 데이터
public class UserResponseDto {
    private String userId;
    private String nickname;
    private String token;
    private boolean consentRequired;

    public static UserResponseDto from(skincare.domain.user.entity.User u) {
        return UserResponseDto.builder()
                .userId(u.getUserId())
                .nickname(u.getNickname())
                .consentRequired(!u.hasRequiredConsents())
                .build();
    }
}
