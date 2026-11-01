package skincare.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserSettings;
import skincare.repository.UserRepository;
import skincare.exception.ErrorCode;
import skincare.repository.UserSettingsRepository;
import skincare.security.AccountStatusChecker;

import java.util.Map;

// OIDC(구글) 소셜 로그인 사용자 정보 조회 및 회원 자동 등록 서비스
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository; // 사용자 데이터 베이스 접근용 Repository
    private final UserSettingsRepository userSettingsRepository; // 사용자 환경설정 데이터 베이스 접근용 Repository

    // 구글 OIDC 사용자 정보를 조회하여 기존 회원 갱신 또는 신규 회원 등록
    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        Map<String, Object> attributes = oidcUser.getAttributes();

        // 구글 사용자 속성 추출 (sub: 구글 유저 고유 ID)
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");
        String sub = (String) attributes.get("sub");

        // 이메일 기반으로 기존 유저 확인 후 프로필 갱신, 없으면 신규 등록
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.updateProfile(name, picture);
                    return existingUser;
                })
                .orElseGet(() -> registerGoogleUser(email, name, picture, sub));

        // 탈퇴/정지 계정은 소셜 로그인 차단 (예외 발생 시 프로필 갱신도 롤백)
        ErrorCode blockedReason = AccountStatusChecker.resolveBlockedReason(user.getStatus());
        if (blockedReason != null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(blockedReason.getCode()), blockedReason.getMessage());
        }

        return new CustomOAuth2User(user, attributes, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    // 구글 소셜 로그인 신규 사용자 등록 및 기본 환경설정 생성
    private User registerGoogleUser(String email, String name, String picture, String sub) {
        // 소셜 로그인 사용자의 고유 userId 생성 (예: google_123456789)
        String userId = "google_" + sub;

        User user = User.builder()
                .userId(userId)
                .email(email)
                .nickname(name != null ? name : "GoogleUser")
                .profileImageUrl(picture)
                .provider(Provider.GOOGLE)
                .agreedTerms(true)
                .agreedPrivacy(true)
                .build();

        userRepository.saveAndFlush(user);

        // 기본 환경설정 자동 생성
        UserSettings settings = UserSettings.builder()
                .user(user)
                .build();
        userSettingsRepository.save(settings);

        return user;
    }
}