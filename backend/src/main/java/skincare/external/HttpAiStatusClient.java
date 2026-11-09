package skincare.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
// HttpAiStatusClient 외부 서비스 연동
public class HttpAiStatusClient implements AiStatusClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
    private final RestClient client;
    private final String token;

    public HttpAiStatusClient(
            @Value("${skincare.ai.base-url:http://127.0.0.1:8000}") String url,
            @Value("${skincare.ai.token:}") String token) {
        var uri = URI.create(url);
        if (!Set.of("http", "https").contains(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null)
            throw new IllegalArgumentException("Invalid AI service base URL");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.token = token;
    }

    private Map<String, Object> result(String status) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", status);
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    private String label(Object value) {
        // Operational labels only, never a path, token or arbitrary upstream response.
        return value instanceof String s && s.matches("[A-Za-z0-9._: -]{1,100}") ? s : "unreported";
    }

    @Override
    public Map<String, Object> status() {
        if (token.isBlank()) return result("UNCONFIGURED");
        try {
            var raw =
                    client.get()
                            .uri("/api/v1/ai/health")
                            .headers(h -> h.setBearerAuth(token))
                            .retrieve()
                            .body(MAP);
            if (raw == null
                    || !(raw.get("status") instanceof String state)
                    || !Set.of("ok", "degraded").contains(state)) return result("INVALID_RESPONSE");
            var result = result(state.equals("ok") ? "READY" : "DEGRADED");
            if (raw.get("models") instanceof Map<?, ?> models)
                result.put(
                        "reportedModels",
                        Map.of(
                                "detect",
                                label(models.get("detect")),
                                "classify",
                                label(models.get("classify"))));
            result.put("bankVersion", label(raw.get("bank_version")));
            result.put("cardVersion", label(raw.get("card_version")));
            result.put("llmConfigured", Boolean.TRUE.equals(raw.get("llm_configured")));
            for (String key : List.of("inflight", "max_inflight")) {
                Object value = raw.get(key);
                if (value instanceof Number n
                        && n.doubleValue() >= 0
                        && n.doubleValue() <= 1000000
                        && n.doubleValue() == n.longValue()) result.put(key, n.longValue());
            }
            return result;
        } catch (HttpClientErrorException e) {
            return result(
                    e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403
                            ? "AUTH_ERROR"
                            : "UNAVAILABLE");
        } catch (RestClientException e) {
            return result("UNAVAILABLE");
        }
    }
}
