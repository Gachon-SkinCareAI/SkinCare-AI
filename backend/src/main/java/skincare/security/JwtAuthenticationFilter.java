package skincare.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import skincare.domain.user.entity.User;
import skincare.exception.ErrorCode;
import skincare.repository.UserRepository;

import java.io.IOException;
import java.util.Collections;

// 요청당 한 번 실행되는 JWT 인증 필터
@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 인증 실패 사유(ErrorCode)를 JwtAuthenticationEntryPoint로 전달하기 위한 request 속성 키
    public static final String AUTH_ERROR_ATTRIBUTE = "skincare.auth.error";

    private final JwtTokenProvider jwtTokenProvider; // JWT 토큰 검증 및 정보 추출 객체
    private final UserRepository userRepository;     // 사용자 계정 상태(탈퇴/정지) 확인용 Repository

    // 필터링 로직 수행 (토큰 검증 및 SecurityContext에 인증 정보 저장)
    // 토큰 원문 및 Authorization 헤더는 탈취 위험이 있으므로 로그에 기록하지 않음
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 요청 헤더에서 JWT 토큰 추출
        String jwtToken = getToken(request);

        if (jwtToken != null) {
            // 토큰 유효성 검증 (실패 시 사유만 기록하고 미인증 상태로 다음 필터 진행)
            ErrorCode tokenError = jwtTokenProvider.resolveTokenError(jwtToken);
            if (tokenError != null) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, tokenError);
            } else {
                authenticate(request, jwtTokenProvider.getClaims(jwtToken));
            }
        }

        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }

    // 토큰의 ID/권한으로 인증 객체 생성 후 SecurityContext에 저장
    // 일반 사용자 토큰은 탈퇴/정지 여부를 확인하여, 상태 변경 이후 기존 토큰으로 접근하는 것을 차단
    private void authenticate(HttpServletRequest request, Claims claims) {
        String id = claims.getSubject();
        String role = (String) claims.get("role");

        if ("USER".equals(role)) {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.USER_NOT_FOUND);
                return;
            }
            ErrorCode blockedReason = AccountStatusChecker.resolveBlockedReason(user.getStatus());
            if (blockedReason != null) {
                log.warn("[JWT] 이용이 차단된 계정의 토큰 사용 시도 - userId: {}, status: {}", id, user.getStatus());
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, blockedReason);
                return;
            }
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                id, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("[JWT] 인증 완료 - id: {}, role: {}", id, role);
    }

    // 클라이언트 요청 헤더에서 Bearer 토큰 추출
    private String getToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
