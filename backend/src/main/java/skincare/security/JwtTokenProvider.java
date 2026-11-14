package skincare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

// JWT 토큰 생성, 해독, 유효성 검증 클래스
@Slf4j
@Component
// JwtTokenProvider 인증과 권한 처리
public class JwtTokenProvider {

    // application.properties 설정 파일에서 secret key 주입 (기본값 설정 포함)
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.admin-access-minutes:15}")
    private long adminAccessMinutes = 15;

    @Value("${jwt.user-access-minutes:60}")
    private long userAccessMinutes = 60;

    private Key key;

    // 비밀키 인코딩 및 서명키 초기화
    @PostConstruct
    protected void init() {
        if (adminAccessMinutes < 1 || adminAccessMinutes >= 1440
                || userAccessMinutes < 1 || userAccessMinutes >= 1440)
            throw new IllegalStateException("JWT access lifetimes must be between 1 and 1439 minutes");
        byte[] secret = secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (secret.length < 32)
            throw new IllegalStateException("jwt.secret must contain at least 32 UTF-8 bytes");
        this.key = Keys.hmacShaKeyFor(secret);
    }

    // JWT 토큰 생성 (ID 및 Role 권한 포함)
    public String createToken(String id, String role, String tokenVersion) {
        Claims claims = Jwts.claims().setSubject(id);
        claims.put("role", role);
        claims.put("ver", tokenVersion);

        Date now = new Date();
        Date validity = new Date(now.getTime() + getTokenValiditySeconds(role) * 1000L);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims verifiedClaims(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        Date issuedAt = claims.getIssuedAt();
        long now = System.currentTimeMillis();
        if (issuedAt == null || claims.getExpiration() == null
                || issuedAt.getTime() > now
                || now - issuedAt.getTime() >= getTokenValiditySeconds(claims.get("role", String.class)) * 1000L)
            throw new JwtException("JWT is outside its allowed lifetime");
        return claims;
    }

    // 토큰에서 ID(Subject) 추출
    public String getId(String token) {
        return verifiedClaims(token).getSubject();
    }

    // 토큰에서 Role(권한) 추출
    public String getRole(String token) {
        return verifiedClaims(token).get("role", String.class);
    }

    public String getTokenVersion(String token) {
        return verifiedClaims(token).get("ver", String.class);
    }

    // 토큰 유효성 및 만료 여부 검증
    public boolean validateToken(String token) {
        try {
            verifiedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT rejected: {}", e.getClass().getSimpleName());
        }
        return false;
    }

    public long getTokenValiditySeconds(String role) {
        return switch (role == null ? "" : role) {
            case "ADMIN" -> adminAccessMinutes * 60L;
            case "USER" -> userAccessMinutes * 60L;
            case "GUEST" -> 86400L;
            default -> throw new IllegalArgumentException("Unsupported JWT role");
        };
    }
}
