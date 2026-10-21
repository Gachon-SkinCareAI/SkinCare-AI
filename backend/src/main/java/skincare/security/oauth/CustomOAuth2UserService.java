package skincare.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserSettings;
import skincare.repository.UserRepository;
import skincare.repository.UserSettingsRepository;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");
        String sub = (String) attributes.get("sub"); // 구글 유저 고유 ID

        // 이메일 기반으로 기존 유저 확인
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    // 이미 존재하는 경우 프로필 업데이트
                    existingUser.updateProfile(name, picture);
                    return existingUser;
                })
                .orElseGet(() -> registerGoogleUser(email, name, picture, sub));

        return new CustomOAuth2User(user, attributes);
    }

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