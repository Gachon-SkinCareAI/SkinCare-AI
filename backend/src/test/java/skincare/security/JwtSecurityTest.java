package skincare.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import skincare.domain.user.entity.User;
import skincare.repository.UserRepository;
import skincare.repository.AdminRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtSecurityTest {
    private JwtTokenProvider provider(String secret) throws Exception {
        var provider = new JwtTokenProvider();
        Field field = JwtTokenProvider.class.getDeclaredField("secretKey");
        field.setAccessible(true); field.set(provider, secret); provider.init();
        return provider;
    }
    private JwtTokenProvider provider() throws Exception {
        return provider("01234567890123456789012345678901");
    }
    @Test void signatureFailureReturnsFalse() throws Exception {
        var original = provider();
        var other = provider("abcdefghijklmnopqrstuvwxyz012345");
        assertFalse(original.validateToken(other.createToken("u", "USER", "0")));
    }
    @Test void rotatedTokenIsRejectedAndNewTokenWorks() throws Exception {
        var provider = provider();
        var user = User.builder().provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).userId("u").email("u@example.test").nickname("u").build();
        user.acceptConsents("2026-11-03", "2026-11-03"); user.acceptHealthConsent("2026-11-03");
        String oldToken = provider.createToken("u", "USER", user.currentTokenVersion());
        user.revokeTokens();
        var users = mock(UserRepository.class);
        when(users.findById("u")).thenReturn(Optional.of(user));
        var filter = new JwtAuthenticationFilter(provider, users, mock(AdminRepository.class));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + oldToken);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("revoked token passed"));
        assertEquals(401, response.getStatus());
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createToken("u", "USER", user.currentTokenVersion()));
        var reached = new AtomicBoolean();
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
            assertTrue(reached.get());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void withdrawnUserCannotUseSignedToken() throws Exception {
        var provider = provider();
        var user = User.builder().provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).userId("u").build(); user.withdraw();
        var users = mock(UserRepository.class); when(users.findById("u")).thenReturn(Optional.of(user));
        var filter = new JwtAuthenticationFilter(provider, users, mock(AdminRepository.class));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createToken("u", "USER", "0"));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("withdrawn account passed"));
        assertEquals(401, response.getStatus());
    }
    @Test void missingConsentOnlyGrantsOnboarding() throws Exception {
        var provider = provider();
        var user = User.builder().provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).userId("u").build();
        var users = mock(UserRepository.class); when(users.findById("u")).thenReturn(Optional.of(user));
        var filter = new JwtAuthenticationFilter(provider, users, mock(AdminRepository.class));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createToken("u", "USER", "0"));
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            assertEquals("ROLE_ONBOARDING", SecurityContextHolder.getContext().getAuthentication()
                    .getAuthorities().iterator().next().getAuthority());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
