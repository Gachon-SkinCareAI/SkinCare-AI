package skincare.security.oauth;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 모바일 앱에서 전달받은 Google ID Token(JWT) 검증 컴포넌트 (POST /api/wc/auth/social/google)
// - 서명: Google 공개키(JWKS)로 검증 (공개키는 Nimbus 라이브러리가 캐시)
// - 발급자(iss): accounts.google.com, 만료(exp), 대상(aud): 등록된 Google Client ID
// - email_verified=true 인 계정만 허용 (미인증 이메일로 기존 회원 계정에 로그인하는 것을 방지)
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    // 웹 OAuth2 로그인용 Google Client ID
    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String webClientId;

    // 모바일 앱(Android/iOS)용 Google Client ID 목록 (쉼표 구분, 앱별 Client ID가 다른 경우 추가)
    @Value("${google.allowed-client-ids:}")
    private String additionalClientIds;

    private JwtDecoder jwtDecoder; // Google ID Token 디코더

    // 허용 Client ID 목록과 검증 규칙을 적용한 JwtDecoder 초기화
    @PostConstruct
    protected void init() {
        Set<String> allowedAudiences = Stream.concat(Stream.of(webClientId), Arrays.stream(additionalClientIds.split(",")))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_issuer"));
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience() != null
                && jwt.getAudience().stream().anyMatch(allowedAudiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), issuerValidator, audienceValidator));
        this.jwtDecoder = decoder;
    }

    // ID Token 검증 후 구글 사용자 정보 반환 (검증 실패 시 AUTH-004)
    public GoogleUserInfo verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
                throw new CustomException(ErrorCode.INVALID_TOKEN, "이메일 인증이 완료되지 않은 구글 계정입니다.");
            }
            return new GoogleUserInfo(jwt.getSubject(), jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
        } catch (CustomException e) {
            throw e;
        } catch (JwtException e) {
            log.warn("[Auth] 인증 실패, 유효하지 않은 Google ID Token: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Google 로그인 토큰입니다.");
        }
    }

    // 검증된 Google ID Token의 사용자 정보
    public record GoogleUserInfo(String sub, String email, String name, String picture) {
    }
}
