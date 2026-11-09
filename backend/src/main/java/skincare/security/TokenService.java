package skincare.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import skincare.domain.global.RotatingRefreshToken;
import skincare.domain.user.entity.User;
import skincare.dto.AuthTokenDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.AdminRepository;
import skincare.repository.RotatingRefreshTokenRepository;
import skincare.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

// Access Token / Refresh Token 발급, 재발급(Rotation), 폐기 서비스
// - Refresh Token은 예측 불가능한 난수(256bit)로 생성하고 DB에는 SHA-256 해시만 저장
// - 재발급 시 사용한 Refresh Token은 즉시 폐기하고 새 토큰을 발급하여 탈취 토큰 재사용을 차단
@Slf4j
@Service
@RequiredArgsConstructor
// TokenService 인증과 권한 처리
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom(); // Refresh Token 난수 생성기

    private final JwtTokenProvider jwtTokenProvider; // Access Token(JWT) 생성
    private final RotatingRefreshTokenRepository refreshTokenRepository; // Refresh Token 해시 저장소
    private final UserRepository userRepository; // 사용자 계정 상태 확인
    private final AdminRepository adminRepository; // 관리자 계정 존재 확인

    // Refresh Token 유효기간 (일, 기본 14일)
    @Value("${jwt.refresh-token-validity-days:14}")
    private long refreshTokenValidityDays;

    // Access Token + Refresh Token 신규 발급 (role: USER, ADMIN)
    @Transactional
    public AuthTokenDto issue(String subjectId, String role) {
        // 만료된 토큰 정리
        refreshTokenRepository.deleteExpired(LocalDateTime.now());

        String rawRefreshToken = generateRawToken();
        refreshTokenRepository.save(
                RotatingRefreshToken.builder()
                        .tokenHash(hash(rawRefreshToken))
                        .subjectId(subjectId)
                        .role(role)
                        .expiresAt(LocalDateTime.now().plusDays(refreshTokenValidityDays))
                        .build());

        return AuthTokenDto.builder()
                .accessToken(
                        jwtTokenProvider.createToken(
                                subjectId, role, tokenVersion(subjectId, role)))
                .expiresIn(jwtTokenProvider.getTokenValiditySeconds())
                .refreshToken(rawRefreshToken)
                .refreshExpiresIn(refreshTokenValidityDays * 24 * 60 * 60)
                .build();
    }

    // Refresh Token으로 토큰 재발급 (사용한 Refresh Token은 폐기 후 새로 발급)
    // 만료/계정 정지로 재발급이 거부되어도 사용된 토큰 폐기는 유지되도록 CustomException은 롤백하지 않음
    @Transactional(noRollbackFor = CustomException.class)
    public AuthTokenDto reissue(String rawRefreshToken, String expectedRole) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "refresh_token이 필요합니다.");
        }

        RotatingRefreshToken stored =
                refreshTokenRepository
                        .findById(hash(rawRefreshToken))
                        .filter(token -> token.getRole().equals(expectedRole))
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.INVALID_TOKEN,
                                                "유효하지 않은 리프레시 토큰입니다. 다시 로그인해 주세요."));

        // 사용한 토큰 즉시 폐기 (만료 여부와 관계없이)
        refreshTokenRepository.delete(stored);

        if (stored.isExpired()) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED, "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요.");
        }

        // 계정 상태 재확인 (탈퇴/정지 회원, 삭제된 관리자는 재발급 불가)
        if ("USER".equals(expectedRole)) {
            User user =
                    userRepository
                            .findById(stored.getSubjectId())
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            ErrorCode blockedReason = AccountStatusChecker.resolveBlockedReason(user.getStatus());
            if (blockedReason != null) {
                throw new CustomException(blockedReason);
            }
        } else if (!adminRepository.existsById(stored.getSubjectId())) {
            throw new CustomException(ErrorCode.ADMIN_NOT_FOUND);
        }

        log.info(
                "[Auth] 토큰 재발급 완료 (subject_id: {}, role: {})", stored.getSubjectId(), expectedRole);
        return issue(stored.getSubjectId(), expectedRole);
    }

    // 로그아웃: 전달받은 Refresh Token 폐기 (토큰이 없으면 해당 계정의 모든 Refresh Token 폐기)
    @Transactional
    public void revoke(String subjectId, String role, String rawRefreshToken) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokenRepository
                    .findById(hash(rawRefreshToken))
                    .filter(
                            token ->
                                    token.getSubjectId().equals(subjectId)
                                            && token.getRole().equals(role))
                    .ifPresent(refreshTokenRepository::delete);
        } else {
            revokeAll(subjectId, role);
        }
    }

    // 계정의 모든 Refresh Token 폐기 (탈퇴, 계정 정지, 전체 로그아웃)
    @Transactional
    public void revokeAll(String subjectId, String role) {
        refreshTokenRepository.deleteAllBySubject(subjectId, role);
    }

    // 256bit 난수 Refresh Token 원문 생성 (URL-safe Base64)
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Refresh Token 원문의 SHA-256 해시 (DB 저장/조회 키)
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private String tokenVersion(String id, String role) {
        if ("USER".equals(role)) {
            var user =
                    userRepository
                            .findById(id)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            if (user.getProvider() == skincare.domain.user.entity.ENUM.Provider.GUEST
                    || user.getProvider() == skincare.domain.user.entity.ENUM.Provider.LOCAL
                    || !user.hasRequiredConsents())
                throw new CustomException(ErrorCode.INVALID_TOKEN);
            return user.currentTokenVersion();
        }
        if ("ADMIN".equals(role))
            return adminRepository
                    .findById(id)
                    .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_NOT_FOUND))
                    .currentTokenVersion();
        throw new CustomException(ErrorCode.INVALID_TOKEN);
    }
}
