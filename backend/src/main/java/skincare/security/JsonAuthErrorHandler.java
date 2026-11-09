package skincare.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import skincare.exception.ErrorCode;
import skincare.exception.ErrorResponse;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Spring Security 필터 단계의 인증/인가 실패를 JSON 에러 응답으로 변환하는 핸들러
// (기본 동작인 OAuth2 로그인 페이지 302 리다이렉트 대신 REST 클라이언트가 처리 가능한 401/403 반환)
// 컨트롤러 예외와 동일한 ErrorResponse 형식을 사용
@Slf4j
@Component
@RequiredArgsConstructor
// JsonAuthErrorHandler 인증과 권한 처리
public class JsonAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper; // 에러 응답 JSON 직렬화용 (Spring Boot 기본 Jackson 설정 공유)

    // 미인증 요청 처리 (토큰 없음/만료/위조, 탈퇴·정지 계정) → 401 또는 403
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        // JwtAuthenticationFilter가 기록한 실패 사유가 있으면 사용, 토큰 자체가 없으면 로그인 필요
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        ErrorCode errorCode =
                attribute instanceof ErrorCode code ? code : ErrorCode.AUTHENTICATION_REQUIRED;

        log.warn(
                "[Auth] 인증 실패 - {} {} ({})",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.getCode());
        writeErrorResponse(response, errorCode);
    }

    // 권한 부족 요청 처리 (ex. 일반 사용자 토큰으로 관리자 API 호출) → 403
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("[Auth] 접근 권한 없음 - {} {}", request.getMethod(), request.getRequestURI());
        writeErrorResponse(response, ErrorCode.ACCESS_DENIED);
    }

    // ErrorResponse JSON 응답 작성
    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
