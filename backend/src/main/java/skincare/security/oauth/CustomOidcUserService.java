package skincare.security.oauth;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.user.entity.User;
import skincare.exception.CustomException;

import java.util.Map;

// OIDC(구글) 소셜 로그인 사용자 정보 조회 및 회원 자동 등록 서비스 (웹 OAuth2 로그인 흐름)
@Service
@RequiredArgsConstructor
// CustomOidcUserService 인증과 권한 처리
public class CustomOidcUserService extends OidcUserService {

    private final GoogleUserRegistrar googleUserRegistrar; // 구글 회원 조회/등록 공통 컴포넌트

    // 구글 OIDC 사용자 정보를 조회하여 기존 회원 조회 또는 신규 회원 등록
    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        Map<String, Object> attributes = oidcUser.getAttributes();

        try {
            User user =
                    googleUserRegistrar.findOrRegister(
                            (String) attributes.get("email"),
                            (String) attributes.get("name"),
                            (String) attributes.get("picture"),
                            (String) attributes.get("sub"));
            return new CustomOAuth2User(
                    user, attributes, oidcUser.getIdToken(), oidcUser.getUserInfo());
        } catch (CustomException e) {
            // 탈퇴/정지 계정 등은 OAuth2 인증 실패로 변환
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(e.getErrorCode().getCode()), e.getMessage());
        }
    }
}
