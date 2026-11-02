package skincare.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.user.entity.User;
import skincare.exception.CustomException;

import java.util.Map;

// OAuth2 소셜 로그인 사용자 정보 조회 및 회원 자동 등록 서비스 (openid scope 없이 로그인하는 경우)
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final GoogleUserRegistrar googleUserRegistrar; // 구글 회원 조회/등록 공통 컴포넌트

    // 소셜 제공자로부터 사용자 정보를 조회하여 기존 회원 조회 또는 신규 회원 등록
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        try {
            User user = googleUserRegistrar.findOrRegister(
                    (String) attributes.get("email"),
                    (String) attributes.get("name"),
                    (String) attributes.get("picture"),
                    (String) attributes.get("sub")); // sub: 구글 유저 고유 ID
            return new CustomOAuth2User(user, attributes);
        } catch (CustomException e) {
            // 탈퇴/정지 계정 등은 OAuth2 인증 실패로 변환
            throw new OAuth2AuthenticationException(new OAuth2Error(e.getErrorCode().getCode()), e.getMessage());
        }
    }
}
