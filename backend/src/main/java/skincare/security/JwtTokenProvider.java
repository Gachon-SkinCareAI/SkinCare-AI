package skincare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

// JWT 토큰 생성, 해독, 유효성 검증 클래스
@Slf4j
@Component
public class JwtTokenProvider {

    // application.properties 설정 파일에서 secret key 주입 (기본값 설정 포함)
    @Value("${jwt.secret:defaultSecretKeyForJwtSigningThatIsAtLeast32BytesLong!}")
    private String secretKey;

    private final long tokenValidityInMilliseconds = 1000L * 60 * 60 * 24; // 토큰 유효시간 (24시간)

    private Key key;

    // 비밀키 인코딩 및 서명키 초기화
    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(Base64.getEncoder().encodeToString(secretKey.getBytes()).getBytes());
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

    // 토큰에서 ID(Subject) 추출
    public String getId(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    // 토큰에서 Role(권한) 추출
    public String getRole(String token) {
        return (String) Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().get("role");
    }

    // 토큰 유효성 및 만료 여부 검증
    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰 형식입니다.");
        } catch (MalformedJwtException e) {
            log.warn("손상된 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어있습니다.");
        }
        return false;
    }
}