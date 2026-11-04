package skincare.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class HttpAnalysisAiClient implements AnalysisAiClient {
    private static final org.slf4j.Logger PERFORMANCE = org.slf4j.LoggerFactory.getLogger("skincare.performance");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private final RestClient client;
    private final String token;
    public HttpAnalysisAiClient(@Value("${skincare.ai.base-url:http://127.0.0.1:8000}") String url,
            @Value("${skincare.ai.token:}") String token,
            @Value("${skincare.ai.read-timeout-seconds:30}") int timeout) {
        var uri = URI.create(url);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Invalid AI service base URL");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build(); this.token = token;
    }
    private Map<String, Object> call(String operation, Supplier<Map<String, Object>> action) {
        if (token.isBlank()) throw new AiUnavailableException();
        long started = System.nanoTime();
        boolean completed = false;
        try {
            var result = action.get();
            if (result == null) throw new AiUnavailableException();
            completed = true;
            return result;
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new skincare.exception.CustomException(skincare.exception.ErrorCode.INVALID_INPUT_VALUE,
                    "AI가 입력을 처리할 수 없습니다. 답변을 확인해 주세요.");
        } catch (RestClientException e) {
            // Do not expose upstream response bodies, images, tokens or symptom text.
            throw new AiUnavailableException();
        } finally {
            PERFORMANCE.info("event=ai_request operation={} completed={} elapsed_ms={}",
                    operation, completed, (System.nanoTime() - started) / 1_000_000);
        }
    }
    @Override public Map<String, Object> predict(byte[] bytes, String contentType) {
        var parts = new LinkedMultiValueMap<String, Object>();
        var resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "upload" + (contentType.equals("image/png") ? ".png" : ".jpg"); }
        };
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        parts.add("file", new org.springframework.http.HttpEntity<>(resource, headers));
        return call("predict", () -> client.post().uri("/api/v1/ai/predict").headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(MAP));
    }
    private Map<String, Object> post(String path, Map<String, Object> body) {
        return call(path, () -> client.post().uri("/api/v1/ai/" + path).headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(MAP));
    }
    @Override public Map<String, Object> question(Map<String, Object> body) { return post("question", body); }
    @Override public Map<String, Object> analyze(Map<String, Object> body) { return post("analyze", body); }
    @Override public Map<String, Object> guide(Map<String, Object> body) { return post("guide", body); }
}
