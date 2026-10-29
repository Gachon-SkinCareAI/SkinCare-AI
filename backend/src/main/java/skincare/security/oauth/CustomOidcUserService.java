package skincare.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        Map<String, Object> attributes = oidcUser.getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");
        String sub = (String) attributes.get("sub");

        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.updateProfile(name, picture);
                    return existingUser;
                })
                .orElseGet(() -> registerGoogleUser(email, name, picture, sub));

        return new CustomOAuth2User(user, attributes, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private User registerGoogleUser(String email, String name, String picture, String sub) {
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

        UserSettings settings = UserSettings.builder()
                .user(user)
                .build();
        userSettingsRepository.save(settings);

        return user;
    }
}