package skincare.external;

import skincare.dto.ai.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;

import java.util.*;

/** Adapts FastAPI's wire format to the existing backend service DTOs. */
// AiProtocol 외부 서비스 연동
public final class AiProtocol {
    private AiProtocol() {}

    public record Quality(Boolean ok, Integer width, Integer height, List<String> reasons) {}

    public record Detection(List<Double> box, Double score) {}

    public record Classification(
            String label,
            Map<String, Double> probabilities,
            List<String> gateFail,
            Boolean ood,
            Double energy) {}

    public record Prediction(
            String requestId,
            Quality quality,
            Detection detection,
            Classification classification,
            Map<String, String> modelVersions) {
        public AiPredictResponseDto toResult() {
            if (quality == null || quality.ok() == null)
                throw new CustomException(ErrorCode.MODEL_INFERENCE_FAILED);
            if (!quality.ok()) throw new CustomException(ErrorCode.IMAGE_QUALITY_FAILED);
            if (classification == null
                    || classification.label() == null
                    || classification.gateFail() == null)
                throw new CustomException(ErrorCode.MODEL_INFERENCE_FAILED);
            boolean passed =
                    classification.gateFail().isEmpty()
                            && Boolean.FALSE.equals(classification.ood())
                            && Set.of("abrasion", "bruise", "burn", "open_wound", "normal_skin")
                                    .contains(classification.label());
            Double confidence =
                    classification.probabilities() == null
                            ? null
                            : classification.probabilities().values().stream()
                                    .filter(Objects::nonNull)
                                    .max(Double::compareTo)
                                    .orElse(null);
            List<Integer> box = null;
            if (detection != null
                    && detection.box() != null
                    && detection.box().size() == 4
                    && quality.width() != null
                    && quality.height() != null) {
                box = new ArrayList<>();
                for (int i = 0; i < 4; i++)
                    box.add(
                            (int)
                                    Math.round(
                                            detection.box().get(i)
                                                    * (i % 2 == 0
                                                            ? quality.width()
                                                            : quality.height())));
            }
            return new AiPredictResponseDto(
                    requestId,
                    passed ? classification.label() : "UNKNOWN",
                    confidence,
                    passed,
                    passed ? null : String.join(",", classification.gateFail()),
                    box,
                    detection == null ? null : detection.score(),
                    classification.energy(),
                    modelVersions,
                    null);
        }
    }

    public record Choice(String value, String label) {}

    public record Question(
            String action,
            String questionId,
            String utterance,
            Map<String, Object> slots,
            String level,
            List<Choice> choices) {
        public AiQuestionResponseDto toResult() {
            if (!Set.of("ask", "finish").contains(action == null ? "" : action)
                    || ("ask".equals(action)
                            && (questionId == null || utterance == null || choices == null)))
                throw new CustomException(ErrorCode.QUESTION_ENGINE_FAILED);
            // Preserve readable options and retain the accepted bank values for the next answer.
            var values = new LinkedHashMap<String, String>();
            if (choices != null)
                for (Choice choice : choices) values.put(choice.label(), choice.value());
            return new AiQuestionResponseDto(
                    "finish".equals(action),
                    questionId,
                    utterance,
                    choices == null ? List.of() : choices.stream().map(Choice::label).toList(),
                    slots == null ? Map.of() : slots,
                    values,
                    "EMERGENCY".equals(level));
        }
    }

    private static Map<String, Object> slots(Map<String, Object> input) {
        var result = new LinkedHashMap<String, Object>(input == null ? Map.of() : input);
        result.remove("symptom_text");
        return result;
    }

    private static String symptoms(Map<String, Object> input) {
        return input != null && input.get("symptom_text") instanceof String text ? text : "";
    }

    public static Map<String, Object> questionBody(AiQuestionRequestDto request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("session_id", request.requestId());
        body.put("slots", slots(request.slots()));
        body.put("last_question_id", request.questionId());
        body.put("turn", request.turn());
        body.put(
                "user_text",
                request.answer() == null ? symptoms(request.slots()) : request.answer());
        // Finish confirmation has no selectable answer in the bank.
        if (request.answer() != null && !"common.finish_confirm".equals(request.questionId()))
            body.put("answer", request.answer());
        return body;
    }

    public static Map<String, Object> analyzeBody(AiAnalyzeRequestDto request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("session_id", request.requestId());
        body.put("slots", slots(request.slots()));
        body.put("user_text", symptoms(request.slots()));
        body.put(
                "vision",
                Map.of(
                        "label",
                        request.label() == null ? "UNKNOWN" : request.label(),
                        "gate_passed",
                        Boolean.TRUE.equals(request.gatePassed())));
        return body;
    }

    public static Map<String, Object> guideBody(AiGuideRequestDto request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("level", request.level());
        body.put("rule_ids", request.ruleIds() == null ? List.of() : request.ruleIds());
        body.put("slots", slots(request.slots()));
        return body;
    }
}
