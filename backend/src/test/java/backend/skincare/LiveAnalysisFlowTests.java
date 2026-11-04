package backend.skincare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import skincare.SkincareApplication;
import skincare.domain.user.entity.User;
import skincare.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_LIVE_TEST", matches = "true")
@SpringBootTest(classes = SkincareApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.address=127.0.0.1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:skincare-live;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false", "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret", "spring.mail.username=test", "spring.mail.password=test",
    "jwt.secret=01234567890123456789012345678901"
})
class LiveAnalysisFlowTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired skincare.security.JwtTokenProvider jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @DynamicPropertySource static void aiProperties(DynamicPropertyRegistry registry) {
        registry.add("skincare.ai.base-url", () -> System.getenv("AI_LIVE_URL"));
        registry.add("skincare.ai.token", () -> System.getenv("AI_SERVICE_TOKEN"));
    }
    Map<String, Object> post(RestClient client, String path, Object body) {
        return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
    }
    Map<String, Object> create(RestClient client, byte[] image, String requestId, String parent) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(image) { @Override public String getFilename() { return "synthetic.png"; } });
        parts.add("clientRequestId", requestId); if (parent != null) parts.add("parentId", parent);
        return client.post().uri("/api/wc/analyses").contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(Map.class);
    }
    @Test void actualHttpLoginPhotoQuestionnaireResultResumeTimelineAndEmergency() throws Exception {
        // Synthetic local-only fixture. Does not create real users or claim real consent.
        var user = User.builder().provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).userId("live-fixture").email("fixture@example.test").nickname("테스트 계정")
                .password(encoder.encode("local-fixture-password")).build();
        user.acceptConsents("2026-11-03", "2026-11-03"); user.acceptHealthConsent("2026-11-03"); users.saveAndFlush(user);
        var anonymous = RestClient.create("http://127.0.0.1:" + port);
        var login = Map.of("token",jwt.createToken(user.getUserId(),"USER",user.currentTokenVersion()));
        var client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).defaultHeaders(h -> h.setBearerAuth((String) login.get("token"))).build();
        var image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB); var random = new Random(42);
        for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++) image.setRGB(x, y, random.nextInt(0xffffff));
        var stream = new ByteArrayOutputStream(); ImageIO.write(image, "png", stream);
        String requestId = UUID.randomUUID().toString();
        var current = create(client, stream.toByteArray(), requestId, null);
        assertEquals("QUESTIONNAIRE", current.get("status"));
        assertEquals(current.get("id"), create(client, stream.toByteArray(), requestId, null).get("id"));
        String id = (String) current.get("id");
        current = client.get().uri("/api/wc/analyses/" + id).retrieve().body(Map.class);
        var choices = Map.of("common.systemic", "none", "unsure.context", "abrasion", "common.elapsed", "lt_1h", "abr.site", "other", "abr.dirty", "clean");
        for (int i = 0; i < 11 && !"COMPLETED".equals(current.get("status")); i++) {
            assertNotEquals("RETRY_REQUIRED", current.get("status"));
            var question = (Map<?, ?>) current.get("question"); assertNotNull(question);
            String qid = (String) question.get("question_id");
            var body = new LinkedHashMap<String, Object>(); body.put("clientRequestId", UUID.randomUUID().toString());
            body.put("expectedVersion", current.get("version")); body.put("questionId", qid); body.put("answer", choices.get(qid));
            current = post(client, "/api/wc/analyses/" + id + "/answers", body);
        }
        assertEquals("COMPLETED", current.get("status"));
        assertNotNull(current.get("result")); assertTrue(((List<?>) current.get("answers")).size() >= 5);
        var saved = client.get().uri("/api/wc/analyses/" + id).retrieve().body(Map.class);
        assertEquals(current.get("result"), saved.get("result"));
        assertTrue(client.get().uri("/api/wc/analyses/" + id + "/image").retrieve().body(byte[].class).length > 0);
        var emergency = create(client, stream.toByteArray(), UUID.randomUUID().toString(), id);
        var question = (Map<?, ?>) emergency.get("question"); assertEquals("common.systemic", question.get("question_id"));
        emergency = post(client, "/api/wc/analyses/" + emergency.get("id") + "/answers", Map.of("clientRequestId", UUID.randomUUID().toString(),
                "expectedVersion", emergency.get("version"), "questionId", "common.systemic", "answer", "breath"));
        assertEquals("EMERGENCY", emergency.get("level"));
        assertEquals(current.get("caseId"), emergency.get("caseId"));
        var timeline = client.get().uri("/api/wc/analyses?caseId=" + current.get("caseId")).retrieve().body(List.class);
        assertEquals(2, timeline.size());
        var report = Map.of("transport", "real HTTP: Spring Boot + FastAPI", "synthetic_image", true,
                "normal_flow_status", current.get("status"), "normal_flow_level", current.get("level"),
                "saved_answers", ((List<?>) current.get("answers")).size(), "emergency_level", emergency.get("level"),
                "timeline_records", timeline.size(), "idempotent_create", true, "persisted_resume", true);
        String reportPath = System.getenv("AI_LIVE_REPORT");
        if (reportPath != null) Files.writeString(Path.of(reportPath), json.writeValueAsString(report));
        client.delete().uri("/api/wc/analyses/" + emergency.get("id")).retrieve().toBodilessEntity();
        client.delete().uri("/api/wc/analyses/" + id).retrieve().toBodilessEntity();
    }
}
