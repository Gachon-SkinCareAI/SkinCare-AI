package skincare.dto;
import java.time.LocalDateTime;
public record AccountProfile(String userId,String nickname,String email,String provider,String plan,
    String termsVersion,String privacyVersion,LocalDateTime consentedAt) {}
