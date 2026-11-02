package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import skincare.domain.user.entity.ENUM.PlanType;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.domain.user.entity.User;
import skincare.service.FileStorageService;

import java.time.format.DateTimeFormatter;

// 사용자 기본 정보 응답 DTO (회원가입/로그인/프로필 수정 응답의 user_info 객체)
@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponseDto {

    // 서버 저장 프로필 사진 조회 API 경로
    private static final String PROFILE_IMAGE_PATH = "/api/wc/users/me/profile-image";

    private String userId;          // 사용자 아이디
    private String email;           // 이메일
    private String nickname;        // 닉네임
    private String profileImageUrl; // 프로필 사진 URL (외부 URL 또는 서버 조회 API 경로)
    private Provider provider;      // 가입 경로 (LOCAL, GOOGLE)
    private PlanType plan;          // 이용 플랜 (FREE, PREMIUM)
    private UserStatus status;      // 계정 상태
    private String createdAt;       // 가입 일시
    private String lastAccessAt;    // 최종 접속 일시
    private String updatedAt;       // 정보 수정 일시
    private String deletedAt;       // 탈퇴 처리 일시 (회원 탈퇴 응답에서만 포함)

    // User 엔티티를 응답 DTO로 변환
    public static UserResponseDto from(User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        String profileImage = user.getProfileImageUrl();

        return UserResponseDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(FileStorageService.isStoredFile(profileImage) ? PROFILE_IMAGE_PATH : profileImage)
                .provider(user.getProvider())
                .plan(user.getPlan())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(formatter) : null)
                .lastAccessAt(user.getLastAccessAt() != null ? user.getLastAccessAt().format(formatter) : null)
                .updatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().format(formatter) : null)
                .build();
    }
}
