package skincare.security.oauth;

import lombok.Getter;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import skincare.domain.user.entity.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
// CustomOAuth2User 인증과 권한 처리
public class CustomOAuth2User
        implements org.springframework.security.oauth2.core.oidc.user.OidcUser {

    private final User user;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // hasRole("USER")와 호환되도록 ROLE_USER 지정
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return user.getUserId();
    }

    private org.springframework.security.oauth2.core.oidc.OidcIdToken idToken;
    private org.springframework.security.oauth2.core.oidc.OidcUserInfo userInfo;

    public CustomOAuth2User(
            User user,
            Map<String, Object> attributes,
            org.springframework.security.oauth2.core.oidc.OidcIdToken idToken,
            org.springframework.security.oauth2.core.oidc.OidcUserInfo userInfo) {
        this(user, attributes);
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }
}
