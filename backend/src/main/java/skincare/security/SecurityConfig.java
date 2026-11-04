package skincare.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Spring Security 설정 클래스
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter; // Spring Bean으로 주입

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${skincare.web.allowed-origins:http://localhost:8081,http://127.0.0.1:8081,http://localhost:5173}") String origins) {
        var cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(java.util.Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(java.util.List.of("Location"));
        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors); return source;
    }

    // 비밀번호 암호화에 사용할 PasswordEncoder 빈 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // HTTP 보안 및 필터 체인 설정
    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 비활성화 (REST API 환경)
                .csrf(AbstractHttpConfigurer::disable)
                // CORS 설정 적용
                .cors(cors -> {})
                // 세션을 사용하지 않도록 STATELESS 설정
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);response.setCharacterEncoding("UTF-8");response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            // sendError forwards to /error in an embedded server, potentially replacing 403 with 401.
                            response.setStatus(403);response.setCharacterEncoding("UTF-8");response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"이 요청에 필요한 권한 또는 필수 동의를 확인해 주세요.\"}");
                        }))
                // API 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // Drafts are readable before login; consent still requires configured published documents.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                            "/api/wc/policy-previews/terms", "/api/wc/policy-previews/privacy", "/api/wc/policies/2026-11-03/terms", "/api/wc/policies/2026-11-03/privacy", "/api/wc/policies/2026-11-01.2/terms", "/api/wc/policies/2026-11-01.2/privacy").permitAll()
                        // 회원가입, 로그인 및 OAuth2 인증 경로 접근 허용
                        .requestMatchers(
                                "/api/wc/auth/consent-policy",
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
                        // Public login UI contains no account data; every admin API remains role-protected.
                        .requestMatchers("/admin", "/admin/", "/admin/index.html", "/admin/app.js", "/admin/style.css").permitAll()
                        // ES modules/styles must load before the administrator can sign in.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/admin/src/**").permitAll()
                        // Swagger 문서 관련 경로 접근 허용
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/wc/users/me").hasAnyRole("USER","ONBOARDING")
                        .requestMatchers("/api/wc/users/me/consents", "/api/wc/auth/logout")
                            .hasAnyRole("USER", "ONBOARDING")
                        // 관리자/사용자 전용 URL 권한 설정
                        .requestMatchers("/api/wc/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/wc/**").hasRole("USER")
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // JWT 필터를 UsernamePasswordAuthenticationFilter 이전에 실행되도록 등록
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}