package skincare.security.oauth;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import skincare.domain.user.entity.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

// 소셜 로그인(OAuth2/OIDC) 인증 사용자 정보를 담는 커스텀 사용자 클래스
@Getter
public class CustomOAuth2User implements OAuth2User, OidcUser {

    private final User user; // 서비스 사용자 엔티티
    private final Map<String, Object> attributes; // 소셜 제공자로부터 받은 사용자 속성 정보
    private OidcIdToken idToken; // OIDC ID 토큰 (구글)
    private OidcUserInfo userInfo; // OIDC 사용자 정보 (구글)

    // 기존 OAuth2 생성자
    public CustomOAuth2User(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    // OIDC 전용 생성자 (구글용)
    public CustomOAuth2User(User user, Map<String, Object> attributes, OidcIdToken idToken, OidcUserInfo userInfo) {
        this.user = user;
        this.attributes = attributes;
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    // 소셜 사용자 속성 정보 반환
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    // 사용자 권한 반환 (ROLE_USER 고정)
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // 인증 사용자 식별자로 userId 반환
    @Override
    public String getName() {
        return user.getUserId();
    }

    // OidcUser 구현 메서드
    // OIDC 클레임 정보 반환
    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }

    // OIDC 사용자 정보 반환
    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    // OIDC ID 토큰 반환
    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }
}