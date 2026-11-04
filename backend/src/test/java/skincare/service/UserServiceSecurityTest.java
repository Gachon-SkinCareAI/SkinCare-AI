package skincare.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import skincare.domain.user.entity.User;
import skincare.dto.*;
import skincare.exception.CustomException;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceSecurityTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    private final UserService service = new UserService(users, tokens, encoder, mock(UserSettingsRepository.class), mock(AccountConsentService.class), mock(AccountErasureService.class));

    @Test void localCredentialLoginIsGone() {
        var user = User.builder().userId("u").password("hash").build(); user.withdraw();
        when(users.findById("u")).thenReturn(Optional.of(user));
        var dto = new UserLoginRequestDto(); dto.setUserId("u"); dto.setPassword("password");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.login(dto));
        verifyNoInteractions(jwt);
    }
    @Test void logoutRotatesVersion() {
        var user = User.builder().userId("u").build();
        when(users.lockForAnalysisCreation("u")).thenReturn(Optional.of(user));
        String before = user.currentTokenVersion();
        service.logout("u", new LogoutRequestDto());
        assertNotEquals(before, user.currentTokenVersion());
        verify(tokens).deleteByUserId("u");
    }
    @Test void localSignupIsGone() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.signUp(new UserSignUpRequestDto()));
        verifyNoInteractions(users);
    }
    @Test void consentVersionAndUserActionAreRequired() {
        assertThrows(CustomException.class, () -> ConsentPolicy.validate(false, true, ConsentPolicy.VERSION, ConsentPolicy.VERSION));
        assertThrows(CustomException.class, () -> ConsentPolicy.validate(true, true, "old", ConsentPolicy.VERSION));
        assertDoesNotThrow(() -> ConsentPolicy.validate(true, true, ConsentPolicy.VERSION, ConsentPolicy.VERSION));
    }
}
