package skincare.security.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import skincare.domain.user.entity.ENUM.Provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

record SocialIdentity(
        Provider provider, String subject, String email, String nickname, String picture) {
    static SocialIdentity from(String registration, Map<String, Object> attrs) {
        Provider provider;
        try {
            provider = Provider.valueOf(registration.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw invalid();
        }
        String id, email = null, nickname, picture;
        switch (provider) {
            case GOOGLE -> {
                id = string(attrs.get("sub"));
                nickname = string(attrs.get("name"));
                picture = string(attrs.get("picture"));
                if (Boolean.TRUE.equals(attrs.get("email_verified")))
                    email = string(attrs.get("email"));
            }
            case KAKAO -> {
                Object raw = attrs.get("id");
                id = raw instanceof Number ? raw.toString() : string(raw);
                if (id == null || !id.matches("[1-9][0-9]*")) throw invalid();
                Map<?, ?> account = map(attrs.get("kakao_account")),
                        profile = map(account.get("profile"));
                nickname = string(profile.get("nickname"));
                picture = string(profile.get("profile_image_url"));
                if (Boolean.TRUE.equals(account.get("is_email_valid"))
                        && Boolean.TRUE.equals(account.get("is_email_verified")))
                    email = string(account.get("email"));
            }
            case NAVER -> {
                if (!"00".equals(attrs.get("resultcode"))) throw invalid();
                Map<?, ?> profile = map(attrs.get("response"));
                id = string(profile.get("id"));
                nickname = string(profile.get("nickname"));
                picture = string(profile.get("profile_image"));
                email = string(profile.get("email"));
            }
            default -> throw invalid();
        }
        if (id == null || id.isBlank() || id.length() > 255) throw invalid();
        if (email != null && (email.length() > 254 || !email.contains("@"))) email = null;
        nickname = nickname == null || nickname.isBlank() ? "사용자" : nickname.strip();
        if (nickname.length() > 50)
            nickname =
                    nickname.substring(
                            0,
                            nickname.offsetByCodePoints(
                                    0,
                                    Math.min(40, nickname.codePointCount(0, nickname.length()))));
        if (picture != null && (!picture.startsWith("https://") || picture.length() > 500))
            picture = null;
        return new SocialIdentity(provider, id, email, nickname, picture);
    }

    String hash() {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String string(Object value) {
        return value instanceof String s ? s : null;
    }

    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException("invalid_social_identity");
    }
}
