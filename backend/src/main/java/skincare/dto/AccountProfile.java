package skincare.dto;

import java.time.LocalDateTime;

// AccountProfile 요청과 응답 데이터
public record AccountProfile(
        String userId,
        String nickname,
        String email,
        String provider,
        String plan,
        String termsVersion,
        String privacyVersion,
        LocalDateTime consentedAt) {}
