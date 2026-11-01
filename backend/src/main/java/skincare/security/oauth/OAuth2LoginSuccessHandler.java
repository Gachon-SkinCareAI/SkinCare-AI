package skincare.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import skincare.domain.user.entity.User;
import skincare.security.JwtTokenProvider;

import java.io.IOException;

// OAuth2 소셜 로그인 성공 시 JWT 토큰을 발급하고 프론트엔드로 리다이렉트하는 핸들러
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider; // JWT 토큰 생성 및 검증용 객체

    // 소셜 로그인 인증 성공 후처리 (토큰 발급 및 리다이렉트)
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        // JWT 토큰 발급
        String token = jwtTokenProvider.createToken(user.getUserId(), "USER");

        // 프론트엔드 로그인 완료 페이지로 리다이렉트 (토큰 전달)
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/oauth/callback")
                .queryParam("token", token)
                .queryParam("userId", user.getUserId())
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}