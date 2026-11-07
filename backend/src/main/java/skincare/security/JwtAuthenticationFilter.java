package skincare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.repository.UserRepository;
import skincare.repository.AdminRepository;
import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String id;
            String role;
            String version;
            try {
                var claims = jwtTokenProvider.verifiedClaims(token);
                id = claims.getSubject();
                role = claims.get("role", String.class);
                version = claims.get("ver", String.class);
                if (claims.getExpiration() == null) throw new IllegalArgumentException("Missing expiration");
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException exception) {
                response.sendError(401, "Invalid token");
                return;
            }
            String authority = null;
            if (id != null && version != null && "USER".equals(role)) {
                var user = userRepository.findById(id).orElse(null);
                if (user != null && user.getProvider() != skincare.domain.user.entity.ENUM.Provider.LOCAL
                        && user.getProvider() != skincare.domain.user.entity.ENUM.Provider.GUEST
                        && user.getStatus() == UserStatus.ACTIVE
                        && version.equals(user.currentTokenVersion())) {
                    authority = user.hasRequiredConsents() ? "ROLE_USER" : "ROLE_ONBOARDING";
                }
            } else if (id != null && version != null && "GUEST".equals(role)) {
                var guest=userRepository.findById(id).orElse(null);
                if(guest!=null&&guest.activeGuest()&&version.equals(guest.currentTokenVersion()))authority="ROLE_GUEST";
            } else if (id != null && version != null && "ADMIN".equals(role)) {
                var admin = adminRepository.findById(id).orElse(null);
                if (admin != null && version.equals(admin.currentTokenVersion())) authority = "ROLE_ADMIN";
            }
            if (authority == null) {
                response.sendError(401, "Account or token is no longer active");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    id, null, Collections.singletonList(new SimpleGrantedAuthority(authority))));
        }
        chain.doFilter(request, response);
    }
public static final String AUTH_ERROR_ATTRIBUTE="skincare.authError";
}
