package skincare.service;

import static org.springframework.http.HttpStatus.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import skincare.domain.user.entity.*;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
// GuestSessionService 비즈니스 로직 처리
public class GuestSessionService {
    private final UserRepository users;
    private final ConsentEventRepository events;
    private final JwtTokenProvider jwt;

    public record Start(
            String deviceKey,
            Boolean agreedService,
            Boolean agreedHealthData,
            String policyVersion,
            Boolean trainingGranted,
            String trainingVersion) {}

    public record Resume(String deviceKey) {}

    private String hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new ResponseStatusException(BAD_REQUEST, "비회원 기기 키가 올바르지 않습니다.");
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> response(User u) {
        if (!u.activeGuest()) throw new ResponseStatusException(UNAUTHORIZED, "비회원 접근이 만료되었습니다.");
        return Map.of(
                "token",
                jwt.createToken(u.getUserId(), "GUEST", u.currentTokenVersion()),
                "userId",
                u.getUserId(),
                "guest",
                true,
                "trainingGranted",
                u.isTrainingGranted()
                        && ConsentPolicy.TRAINING_VERSION.equals(u.getTrainingConsentVersion()),
                "policyVersion",
                ConsentPolicy.GUEST_VERSION);
    }

    @Transactional
    public Map<String, Object> start(Start d) {
        if (d == null
                || !Boolean.TRUE.equals(d.agreedService())
                || !Boolean.TRUE.equals(d.agreedHealthData())
                || !ConsentPolicy.GUEST_VERSION.equals(d.policyVersion()))
            throw new ResponseStatusException(BAD_REQUEST, "비회원 분석·보관 안내와 건강정보 처리에 직접 동의해 주세요.");
        String key = hash(d.deviceKey());
        if (Boolean.TRUE.equals(d.trainingGranted())
                && !ConsentPolicy.TRAINING_VERSION.equals(d.trainingVersion()))
            throw new ResponseStatusException(BAD_REQUEST, "현재 학습 이용 안내를 확인해 주세요.");
        var existing = users.findByGuestKeyHash(key);
        if (existing.isPresent()) return response(existing.get());
        var u =
                User.builder()
                        .userId(UUID.randomUUID().toString())
                        .nickname("비회원")
                        .provider(Provider.GUEST)
                        .build();
        u.acceptGuest(key, ConsentPolicy.GUEST_VERSION);
        u.acceptHealthConsent(ConsentPolicy.GUEST_VERSION);
        boolean training = Boolean.TRUE.equals(d.trainingGranted());
        u.trainingConsent(training, ConsentPolicy.TRAINING_VERSION);
        u = users.saveAndFlush(u);
        events.save(
                new ConsentEvent(
                        u.getUserId(), "GUEST_SERVICE", ConsentPolicy.GUEST_VERSION, true));
        events.save(
                new ConsentEvent(u.getUserId(), "HEALTH_DATA", ConsentPolicy.GUEST_VERSION, true));
        events.save(
                new ConsentEvent(
                        u.getUserId(), "MODEL_TRAINING", ConsentPolicy.TRAINING_VERSION, training));
        return response(u);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resume(Resume d) {
        var u =
                users.findByGuestKeyHash(hash(d == null ? null : d.deviceKey()))
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                UNAUTHORIZED, "비회원 기록에 접근할 수 없습니다."));
        return response(u);
    }
}
