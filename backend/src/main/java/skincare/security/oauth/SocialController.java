package skincare.security.oauth;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import skincare.domain.user.entity.ENUM.*;
import skincare.dto.UserResponseDto;
import skincare.repository.UserRepository;
import skincare.security.JwtTokenProvider;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wc/auth/social")
@RequiredArgsConstructor
// SocialController 인증과 권한 처리
public class SocialController {
    private final ClientRegistrationRepository registrations;
    private final SocialFlowStore flows;
    private final UserRepository users;
    private final JwtTokenProvider jwt;

    record ProviderInfo(String id, String label, boolean enabled) {}

    record Start(String provider, String redirectUri, String codeChallenge) {}

    record Exchange(String code, String codeVerifier) {}

    @GetMapping("/providers")
    public ResponseEntity<List<ProviderInfo>> providers() {
        return noStore(List.of(info("google", "구글"), info("kakao", "카카오"), info("naver", "네이버")));
    }

    private ProviderInfo info(String id, String name) {
        return new ProviderInfo(id, name, registrations.findByRegistrationId(id) != null);
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> start(@RequestBody Start input) {
        if (input.provider() == null
                || !List.of("google", "kakao", "naver").contains(input.provider()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다.");
        if (registrations.findByRegistrationId(input.provider()) == null)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "아직 준비 중인 로그인입니다. 잠시 후 다시 이용해 주세요.");
        String key = flows.start(input.provider(), input.redirectUri(), input.codeChallenge());
        return noStore(
                Map.of(
                        "authorizationPath",
                        "/oauth2/authorization/" + input.provider() + "?flow=" + key));
    }

    @PostMapping("/exchange")
    public ResponseEntity<UserResponseDto> exchange(@RequestBody Exchange input) {
        var ticket = flows.exchange(input.code(), input.codeVerifier());
        var user =
                users.findById(ticket.userId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE
                || user.getProvider() == Provider.LOCAL
                || !ticket.tokenVersion().equals(user.currentTokenVersion()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용할 수 없는 계정입니다.");
        return noStore(
                UserResponseDto.builder()
                        .userId(user.getUserId())
                        .nickname(user.getNickname())
                        .token(
                                jwt.createToken(
                                        user.getUserId(), "USER", user.currentTokenVersion()))
                        .consentRequired(!user.hasRequiredConsents())
                        .build());
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
