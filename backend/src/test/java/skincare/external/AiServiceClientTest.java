package skincare.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import skincare.config.AppConfig;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.dto.ai.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiServiceClientTest {
    private AiServiceClient client;
    private MockRestServiceServer server;
    @BeforeEach void setup() {
        var template = new AppConfig().aiRestTemplate();
        server = MockRestServiceServer.bindTo(template).build();
        client = new AiServiceClient(template);
        ReflectionTestUtils.setField(client, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(client, "aiServiceToken", "test-token");
    }
    @Test void decodesActualPredictionWireFormat() {
        server.expect(requestTo("http://localhost:8000/api/v1/ai/predict"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                    {"request_id":"abc","quality":{"ok":true,"width":1000,"height":500},
                     "detection":{"box":[0.1,0.2,0.3,0.4],"score":0.9},
                     "classification":{"label":"burn","probabilities":{"burn":0.85},
                        "gate_fail":[],"ood":false,"energy":-2.0},"model_versions":{"classify":"vit"}}
                    """, MediaType.APPLICATION_JSON));
        var result = client.predict(new MockMultipartFile("file", "wound.png", "image/png", new byte[]{1}),
                BodyPart.values()[0]);
        assertEquals("burn", result.label()); assertTrue(result.gatePassed());
        assertEquals("vit", result.modelVersion().get("classify"));
        server.verify();
    }
    @Test void serializesQuestionAndDecodesReadableChoices() {
        server.expect(requestTo("http://localhost:8000/api/v1/ai/question"))
                .andExpect(content().json("""
                    {"session_id":"abc","slots":{},"last_question_id":"common.systemic",
                     "answer":"none","user_text":"none","turn":1}
                    """))
                .andRespond(withSuccess("""
                    {"action":"ask","question_id":"common.elapsed","utterance":"언제인가요?",
                     "slots":{"systemic":["none"]},"level":null,
                     "choices":[{"value":"lt_1h","label":"한 시간 안"}]}
                    """, MediaType.APPLICATION_JSON));
        var result = client.question(new AiQuestionRequestDto("abc", "burn", Map.of(), "common.systemic", "none", 1));
        assertEquals("언제인가요?", result.question());
        assertEquals("lt_1h", result.optionValues().get("한 시간 안"));
        assertFalse(result.done()); server.verify();
    }
    @Test void incompleteAnalysisRemainsUnknownAndIncludesVision() {
        server.expect(requestTo("http://localhost:8000/api/v1/ai/analyze"))
                .andExpect(content().json("""
                    {"session_id":"abc","slots":{},"user_text":"숨쉬기 어렵습니다",
                     "vision":{"label":"UNKNOWN","gate_passed":false}}
                    """))
                .andRespond(withSuccess("""
                    {"level":null,"rule_ids":[],"missing_slots":["scenario"],"scenario_source":null}
                    """, MediaType.APPLICATION_JSON));
        var result = client.analyze(new AiAnalyzeRequestDto("abc", "UNKNOWN", null, false,
                Map.of("symptom_text", "숨쉬기 어렵습니다")));
        assertEquals(RiskLevel.UNKNOWN, result.level());
        assertEquals(List.of("scenario"), result.missingSlots()); server.verify();
    }
    @Test void retrainingReasonUsesQueryParameterExpectedByFastApi() {
        server.expect(requestTo("http://localhost:8000/api/v1/ai/retrain?reason=recall_drop"))
                .andRespond(withSuccess("{\"job_id\":\"job\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));
        assertEquals("job", client.retrain("recall_drop").jobId()); server.verify();
    }
}
