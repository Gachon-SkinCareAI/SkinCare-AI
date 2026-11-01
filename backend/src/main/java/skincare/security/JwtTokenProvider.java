package skincare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import skincare.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

// JWT 토큰 생성, 해독, 유효성 검증 클래스
@Slf4j
@Component
public class JwtTokenProvider {

    // HS256 서명키 최소 길이 (32바이트 = 256bit)
    private static final int MIN_SECRET_BYTES = 32;

    // JWT 서명 비밀키 (application.yml의 jwt.secret ← 환경변수 JWT_SECRET)
    // 소스코드에 기본값을 두면 누구나 토큰을 위조할 수 있으므로 반드시 환경변수로만 주입
    @Value("${jwt.secret:}")
    private String secretKey;

    private final long tokenValidityInMilliseconds = 1000L * 60 * 60 * 24; // 토큰 유효시간 (24시간)

    private Key key; // JWT 서명용 HMAC 키

    // 비밀키 검증 및 서명키 초기화 (비밀키 미설정/길이 부족 시 서버 기동 중단)
    @PostConstruct
    protected void init() {
        if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 환경변수가 설정되지 않았거나 32바이트보다 짧습니다. .env에 32자 이상의 임의 문자열을 설정해 주세요.");
        }
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // JWT 토큰 생성 (ID 및 Role 권한 포함)
    public String createToken(String id, String role) {
        Claims claims = Jwts.claims().setSubject(id);
        claims.put("role", role);

        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 토큰 유효시간 (초 단위, 로그인 응답의 expires_in)
    public long getTokenValiditySeconds() {
        return tokenValidityInMilliseconds / 1000;
    }

    // 토큰 서명 검증 후 Claims(Subject, Role 등) 추출
    public Claims getClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }

    // 토큰에서 ID(Subject) 추출
    public String getId(String token) {
        return getClaims(token).getSubject();
    }

    // 토큰에서 Role(권한) 추출
    public String getRole(String token) {
        return (String) getClaims(token).get("role");
    }

    // 토큰 유효성 및 만료 여부 검증
    public boolean validateToken(String token) {
        return resolveTokenError(token) == null;
    }

    // 토큰 검증 실패 원인 반환 (유효한 토큰이면 null)
    // 인증 실패 응답(JwtAuthenticationEntryPoint)에서 만료/형식 오류/위조를 구분하여 안내하기 위해 사용
    public ErrorCode resolveTokenError(String token) {
        try {
            getClaims(token);
            return null;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다.");
            return ErrorCode.TOKEN_EXPIRED;
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰 형식입니다.");
            return ErrorCode.UNSUPPORTED_TOKEN;
        } catch (MalformedJwtException | SignatureException e) {
            log.warn("손상되었거나 서명이 올바르지 않은 JWT 토큰입니다.");
            return ErrorCode.INVALID_TOKEN;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰입니다.");
            return ErrorCode.INVALID_TOKEN;
        }
    }
}
