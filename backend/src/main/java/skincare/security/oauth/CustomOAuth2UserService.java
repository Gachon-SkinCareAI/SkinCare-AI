package skincare.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final SocialAccountService accounts;
    @Override public OAuth2User loadUser(OAuth2UserRequest request) {
        var attrs = super.loadUser(request).getAttributes();
        var identity = SocialIdentity.from(request.getClientRegistration().getRegistrationId(), attrs);
        return new CustomOAuth2User(accounts.authenticate(identity), attrs);
    }
}
