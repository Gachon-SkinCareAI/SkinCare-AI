package skincare.external;

import org.junit.jupiter.api.Test;
import skincare.dto.ai.*;
import skincare.exception.CustomException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiProtocolTest {
    @Test void mapsNestedPredictionAndPixelCoordinates() {
        var result = new AiProtocol.Prediction("id", new AiProtocol.Quality(true, 1000, 500, List.of()),
                new AiProtocol.Detection(List.of(.1, .2, .3, .4), .9),
                new AiProtocol.Classification("burn", Map.of("burn", .85), List.of(), false, -2.), Map.of()).toResult();
        assertEquals("burn", result.label());
        assertTrue(result.gatePassed());
        assertEquals(List.of(100, 100, 300, 200), result.bbox());
        assertEquals(.85, result.confidence());
    }
    @Test void qualityFailureAndOodCannotBecomePassedPrediction() {
        assertThrows(CustomException.class, () -> new AiProtocol.Prediction("id",
                new AiProtocol.Quality(false, 10, 10, List.of("blur")), null, null, null).toResult());
        var result = new AiProtocol.Prediction("id", new AiProtocol.Quality(true, 500, 500, List.of()), null,
                new AiProtocol.Classification("UNKNOWN", null, List.of("ood"), true, null), null).toResult();
        assertFalse(result.gatePassed()); assertEquals("UNKNOWN", result.label());
    }
    @Test void mapsQuestionAndPreservesEmergency() {
        var ask = new AiProtocol.Question("ask", "common.systemic", "증상?", Map.of(), null,
                List.of(new AiProtocol.Choice("none", "없음"))).toResult();
        assertFalse(ask.done()); assertEquals(List.of("없음"), ask.options());
        assertEquals("증상?", ask.question());
        assertEquals("none", ask.optionValues().get("없음"));
        assertTrue(new AiProtocol.Question("finish", null, null, Map.of(), "EMERGENCY", List.of()).toResult().emergency());
    }
    @Test void requestUsesActualQuestionIdAndSeparatesSymptomMetadata() {
        var slots = Map.<String,Object>of("symptom_text", "숨쉬기 어렵습니다", "scenario", "burn");
        var question = AiProtocol.questionBody(new AiQuestionRequestDto("id", "burn", slots,
                "common.systemic", "none", 1));
        assertEquals("common.systemic", question.get("last_question_id"));
        assertEquals("none", question.get("answer"));
        assertFalse(((Map<?,?>)question.get("slots")).containsKey("symptom_text"));
        var analyze = AiProtocol.analyzeBody(new AiAnalyzeRequestDto("id", "UNKNOWN", null, false, slots));
        assertEquals(false, ((Map<?,?>)analyze.get("vision")).get("gate_passed"));
        assertEquals("숨쉬기 어렵습니다", analyze.get("user_text"));
        assertTrue(slots.containsKey("symptom_text"));
    }
    @Test void confirmationIsNotSubmittedAsInvalidChoice() {
        assertFalse(AiProtocol.questionBody(new AiQuestionRequestDto("id", "burn", Map.of(),
                "common.finish_confirm", "확인", 9)).containsKey("answer"));
    }
}
