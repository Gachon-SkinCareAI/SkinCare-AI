package skincare.security;

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

import java.io.IOException;
import java.util.Collections;

// 요청당 한 번 실행되는 JWT 인증 필터
@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider; // JWT 토큰 검증 및 정보 추출 객체

    // 필터링 로직 수행 (토큰 검증 및 SecurityContext에 인증 정보 저장)
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.info("Authorization 헤더: " + request.getHeader("Authorization"));

        // 요청 헤더에서 JWT 토큰 추출
        String jwtToken = getToken(request);

        // 토큰 유효성 검증
        if (jwtToken != null && jwtTokenProvider.validateToken(jwtToken)) {
            // 토큰에서 ID 및 권한 추출
            String id = jwtTokenProvider.getId(jwtToken);
            String role = jwtTokenProvider.getRole(jwtToken);

            log.info("JWT Token: " + jwtToken);
            log.info("ID: " + id);
            log.info("Role: " + role);

            // 인증 객체 생성 및 SecurityContext에 저장
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    id, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
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