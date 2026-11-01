package skincare.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import skincare.security.oauth.CustomOAuth2UserService;
import skincare.security.oauth.CustomOidcUserService;
import skincare.security.oauth.OAuth2LoginSuccessHandler;

import java.util.List;

// Spring Security 설정 클래스
// @EnableMethodSecurity: 컨트롤러의 @PreAuthorize 권한 검사 활성화
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter; // Spring Bean으로 주입
    private final CustomOAuth2UserService customOAuth2UserService; // OAuth2 사용자 서비스
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler; // OAuth2 로그인 성공 핸들러
    private final CustomOidcUserService customOidcUserService; // OIDC(구글) 사용자 서비스
    private final JsonAuthErrorHandler jsonAuthErrorHandler; // 인증/인가 실패 JSON 응답 핸들러

    // 비밀번호 암호화에 사용할 PasswordEncoder 빈 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 전역 CORS 정책 빈 등록 (모든 컨트롤러에 공통 적용, .cors()가 이 빈을 사용)
    // 허용 Origin은 application.yml의 cors.allowed-origins(환경변수 CORS_ALLOWED_ORIGINS)로 관리
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // HTTP 보안 및 필터 체인 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 비활성화 (REST API 환경)
                .csrf(AbstractHttpConfigurer::disable)
                // CORS 설정 적용 (corsConfigurationSource 빈 사용)
                .cors(Customizer.withDefaults())
                // 세션을 사용하지 않도록 STATELESS 설정
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 회원가입, 로그인 및 OAuth2 인증 경로 접근 허용
                        .requestMatchers(
                                "/api/wc/auth/login",
                                "/api/wc/auth/signup",
                                "/api/wc/auth/email/**",
                                "/api/wc/auth/social/**",
                                "/api/wc/auth/password/reset",
                                "/api/wc/auth/password/confirm",
                                "/api/wc/admin/auth/login",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        // 관리자 계정 생성은 기존 관리자만 가능 (최초 관리자는 AdminInitializer가 환경변수로 생성)
                        // ※ /api/wc/admin/** 규칙보다 먼저 선언되어야 함
                        .requestMatchers("/api/wc/admin/signup").hasRole("ADMIN")
                        // Swagger 문서 및 Spring Boot 에러 페이지 경로 접근 허용
                        // (/error를 막으면 실제 오류 대신 401이 반환되어 원인 파악이 어려워짐)
                        .requestMatchers("/swagger-ui/**", "/swagger.html", "/v3/api-docs/**", "/error").permitAll()
                        // 관리자/사용자 전용 URL 권한 설정
                        .requestMatchers("/api/wc/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/wc/**").hasRole("USER")
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // 인증/인가 실패 시 로그인 페이지 리다이렉트(302) 대신 JSON 에러 응답(401/403) 반환
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jsonAuthErrorHandler)
                        .accessDeniedHandler(jsonAuthErrorHandler)
                )
                // OAuth2 로그인 설정 추가
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)   // 카카오, 네이버 등
                                .oidcUserService(customOidcUserService) // 구글 OIDC 설정 추가!
                        )
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                // JWT 필터를 UsernamePasswordAuthenticationFilter 이전에 실행되도록 등록
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}