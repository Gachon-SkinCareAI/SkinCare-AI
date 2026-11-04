package skincare.security.oauth;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import skincare.domain.user.entity.User;

/** Bounded, expiring single-instance store. Contains no provider tokens or application JWTs. */
@Component
public class SocialFlowStore {
    public record Flow(String provider, String redirectUri, String challenge, long expiresAt) implements Serializable {}
    record Ticket(String userId, String tokenVersion, String challenge, long expiresAt) {}
    private final Set<String> redirects;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String,Flow> flows = new HashMap<>();
    private final Map<String,Ticket> tickets = new HashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public SocialFlowStore(@Value("${social.redirect-uris}") String redirects) { this(redirects, Clock.systemUTC()); }
    SocialFlowStore(String redirects, Clock clock) {
        this.clock = clock; this.redirects = new LinkedHashSet<>();
        for (String text : redirects.split(",")) {
            URI uri = URI.create(text.strip());
            boolean web = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && Set.of("localhost","127.0.0.1").contains(uri.getHost()));
            boolean nativeApp = "skincareai://oauth/callback".equals(uri.toString());
            if ((!web && !nativeApp) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getRawQuery() != null)
                throw new IllegalArgumentException("Invalid social redirect allowlist");
            this.redirects.add(uri.toString());
        }
        if (this.redirects.isEmpty()) throw new IllegalArgumentException("Social redirect allowlist is empty");
    }
    public synchronized String start(String provider, String redirect, String challenge) {
        if (!redirects.contains(redirect) || challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}"))
            throw new ResponseStatusException(BAD_REQUEST,"로그인 복귀 주소 또는 인증 요청을 확인해 주세요.");
        cleanup(); capacity(flows.size());
        String key = randomKey(); flows.put(hash(key),new Flow(provider, redirect, challenge, clock.millis()+600_000)); return key;
    }
    synchronized Flow takeFlow(String key, String provider) {
        cleanup(); Flow flow = flows.remove(hash(key == null ? "" : key));
        if (flow == null || !flow.provider().equals(provider)) throw new ResponseStatusException(BAD_REQUEST,"로그인을 처음부터 다시 시작해 주세요.");
        return flow;
    }
    synchronized String issue(User user, Flow flow) {
        cleanup();
        if (flow == null || flow.expiresAt() <= clock.millis()) throw new ResponseStatusException(BAD_REQUEST,"로그인 요청이 만료되었습니다.");
        capacity(tickets.size());
        String code = randomKey(); tickets.put(hash(code),new Ticket(user.getUserId(),user.currentTokenVersion(),flow.challenge(),clock.millis()+60_000)); return code;
    }
    synchronized Ticket exchange(String code, String verifier) {
        cleanup();
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}") || verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw invalidExchange();
        String key = hash(code); Ticket ticket = tickets.get(key);
        if (ticket == null || !MessageDigest.isEqual(ticket.challenge().getBytes(StandardCharsets.US_ASCII), challenge(verifier).getBytes(StandardCharsets.US_ASCII))) throw invalidExchange();
        tickets.remove(key); return ticket;
    }
    String fallbackRedirect() { return redirects.iterator().next(); }
    private void cleanup() {
        long now = clock.millis(); flows.values().removeIf(f -> f.expiresAt() <= now); tickets.values().removeIf(t -> t.expiresAt() <= now);
    }
    private void capacity(int size) { if (size >= 1000) throw new ResponseStatusException(TOO_MANY_REQUESTS,"잠시 후 다시 로그인해 주세요."); }
    private String randomKey() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String challenge(String verifier) { return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier)); }
    private static String hash(String text) { return HexFormat.of().formatHex(digest(text)); }
    private static byte[] digest(String text) {
        try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ResponseStatusException invalidExchange() { return new ResponseStatusException(BAD_REQUEST,"로그인 요청이 만료되었거나 일치하지 않습니다. 다시 로그인해 주세요."); }
}
