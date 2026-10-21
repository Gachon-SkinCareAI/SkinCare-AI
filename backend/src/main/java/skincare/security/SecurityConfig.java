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
import skincare.security.oauth.CustomOAuth2UserService;
import skincare.security.oauth.OAuth2LoginSuccessHandler;

// Spring Security 설정 클래스
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter; // Spring Bean으로 주입
    private final CustomOAuth2UserService customOAuth2UserService; // OAuth2 사용자 서비스
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler; // OAuth2 로그인 성공 핸들러

    // 비밀번호 암호화에 사용할 PasswordEncoder 빈 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // HTTP 보안 및 필터 체인 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 비활성화 (REST API 환경)
                .csrf(AbstractHttpConfigurer::disable)
                // CORS 설정 적용
                .cors(cors -> {})
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
                        // Swagger 문서 관련 경로 접근 허용
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 관리자/사용자 전용 URL 권한 설정
                        .requestMatchers("/api/wc/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/wc/**").hasRole("USER")
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // OAuth2 로그인 설정 추가
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                // JWT 필터를 UsernamePasswordAuthenticationFilter 이전에 실행되도록 등록
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}