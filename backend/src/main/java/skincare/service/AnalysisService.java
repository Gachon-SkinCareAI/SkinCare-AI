package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import skincare.domain.analysis.*;
import skincare.dto.AnalysisAnswerRequest;
import skincare.external.*;
import skincare.repository.*;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Service
@RequiredArgsConstructor
// AnalysisService 비즈니스 로직 처리
public class AnalysisService {
    private final AnalysisSessionRepository sessions;
    private final AnalysisAnswerRepository answers;
    private final UserRepository users;
    private final AnalysisAiClient ai;
    private final ObjectMapper json;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "기록이 변경되었습니다. 다시 불러와 주세요.");
    }

    private AnalysisSession owned(String user, String id, boolean lock) {
        if (lock)
            users.lockForAnalysisCreation(user)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return (lock ? sessions.lockOwned(id, user) : sessions.findByIdAndUserId(id, user))
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "기록을 찾을 수 없습니다."));
    }

    private String uuid(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value))
                throw bad("올바른 요청 ID가 필요합니다.");
        } catch (IllegalArgumentException e) {
            throw bad("올바른 요청 ID가 필요합니다.");
        }
        return value;
    }

    private void version(AnalysisSession session, Long expected) {
        if (expected == null || expected < 0) throw bad("기록 버전이 필요합니다.");
        if (session.getVersion() != expected) throw conflict();
    }

    private Map<String, Object> read(String value) {
        return value == null ? new LinkedHashMap<>() : json.readValue(value, MAP);
    }

    private String write(Map<String, Object> value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) throw new AiUnavailableException();
        return (Map<String, Object>) value;
    }

    @Transactional
    public Map<String, Object> create(
            String user, String requestId, String parentId, AnalysisImage image) {
        return create(user, requestId, parentId, image, null);
    }

    private java.time.LocalDate captureDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            var date = java.time.LocalDate.parse(value);
            if (date.getYear() < 1900
                    || date.isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))))
                throw new IllegalArgumentException();
            return date;
        } catch (java.time.DateTimeException | IllegalArgumentException e) {
            throw bad("촬영일은 1900년부터 오늘까지의 실제 날짜(YYYY-MM-DD)로 입력해 주세요.");
        }
    }

    private String symptom(String value, String previous) {
        if (value == null) return previous; // Older clients must not erase newly recorded metadata.
        if ("UNRECORDED".equals(value)) return null;
        if (!Set.of("NONE", "PRESENT", "UNKNOWN").contains(value))
            throw bad("증상은 없음, 있음, 잘 모르겠음 중에서 선택해 주세요.");
        return value;
    }

    @Transactional
    public Map<String, Object> create(
            String user,
            String requestId,
            String parentId,
            AnalysisImage image,
            String capturedOn) {
        uuid(requestId);
        var date = captureDate(capturedOn);
        // Serialize per-account creation, including idempotent retries, at the database boundary.
        var owner =
                users.lockForAnalysisCreation(user)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String caseId =
                parentId == null || parentId.isBlank()
                        ? null
                        : owned(user, parentId, false).getCaseId();
        var existing = sessions.findByUserIdAndClientRequestId(user, requestId);
        if (existing.isPresent()) {
            var session = existing.get();
            if (!Arrays.equals(session.getImage(), image.bytes())
                    || !Objects.equals(date, session.getCapturedOn())
                    || (caseId == null
                            ? !session.getCaseId().equals(session.getId())
                            : !caseId.equals(session.getCaseId()))) throw conflict();
            return view(session, true);
        }
        if (owner.getProvider() == skincare.domain.user.entity.ENUM.Provider.GUEST
                && sessions.countByUserIdAndCreatedAtAfter(
                                user, java.time.LocalDateTime.now().minusDays(1))
                        >= 20)
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "비회원 분석은 하루 20회까지 이용할 수 있습니다.");
        var session =
                new AnalysisSession(user, requestId, caseId, image.bytes(), image.contentType());
        session.recordCapture(date);
        session.snapshotDataUse(owner);
        session = sessions.saveAndFlush(session);
        advance(session, null);
        session = sessions.saveAndFlush(session);
        return view(session, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String user, String id) {
        return view(owned(user, id, false), true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String user, String caseId, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 50) throw bad("페이지 크기는 1~50이어야 합니다.");
        var pageable = PageRequest.of(page, size);
        // Summary projection keeps potentially large photo blobs out of list queries.
        return sessions.listOwned(user, caseId, pageable).stream()
                .map(
                        s -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("id", s.getId());
                            item.put("caseId", s.getCaseId());
                            item.put("status", s.getStatus());
                            item.put("version", s.getVersion());
                            item.put("turn", s.getTurn());
                            item.put("createdAt", s.getCreatedAt());
                            item.put("capturedOn", s.getCapturedOn());
                            item.put("updatedAt", s.getUpdatedAt());
                            item.put("errorCode", s.getErrorCode());
                            item.put("level", read(s.getResultJson()).get("level"));
                            item.put("imagePath", "/api/wc/analyses/" + s.getId() + "/image");
                            return item;
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisImage image(String user, String id) {
        var session = owned(user, id, false);
        return new AnalysisImage(session.getImage(), session.getImageType());
    }

    @Transactional
    public Map<String, Object> answer(String user, String id, AnalysisAnswerRequest body) {
        if (body == null) throw bad("답변이 필요합니다.");
        uuid(body.clientRequestId());
        var session = owned(user, id, true);
        String text = body.userText() == null ? "" : body.userText().strip();
        if (text.length() > 2000) throw bad("답변은 2000자 이하여야 합니다.");
        var duplicate = answers.findBySessionIdAndClientRequestId(id, body.clientRequestId());
        if (duplicate.isPresent()) {
            var previous = duplicate.get();
            if (!Objects.equals(previous.getQuestionId(), body.questionId())
                    || !Objects.equals(previous.getAnswer(), body.answer())
                    || !Objects.equals(previous.getUserText(), text)) throw conflict();
            return view(session, true);
        }
        version(session, body.expectedVersion());
        if (session.getStatus().equals("COMPLETED")) throw conflict();
        var current = read(session.getQuestionJson());
        if (body.questionId() == null || !body.questionId().equals(current.get("question_id")))
            throw conflict();
        var choices = current.get("choices") instanceof List<?> list ? list : List.of();
        if (body.answer() != null
                && choices.stream()
                        .noneMatch(
                                choice ->
                                        choice instanceof Map<?, ?> map
                                                && body.answer().equals(map.get("value"))))
            throw bad("현재 질문의 선택지에서 답변해 주세요.");
        if (!choices.isEmpty() && body.answer() == null && text.isBlank())
            throw bad("선택지 또는 증상을 입력해 주세요.");
        var normalized =
                new AnalysisAnswerRequest(
                        body.clientRequestId(),
                        body.expectedVersion(),
                        body.questionId(),
                        body.answer(),
                        text);
        if (advance(session, normalized)) {
            String label =
                    choices.stream()
                            .filter(
                                    choice ->
                                            choice instanceof Map<?, ?> map
                                                    && Objects.equals(
                                                            body.answer(), map.get("value")))
                            .map(choice -> String.valueOf(((Map<?, ?>) choice).get("label")))
                            .findFirst()
                            .orElse(null);
            answers.save(
                    new AnalysisAnswer(
                            id,
                            body.clientRequestId(),
                            body.questionId(),
                            body.answer(),
                            text,
                            session.getTurn(),
                            String.valueOf(current.get("utterance")),
                            label));
        }
        sessions.saveAndFlush(session);
        return view(session, true);
    }

    @Transactional
    public Map<String, Object> retry(String user, String id, Long expected) {
        var session = owned(user, id, true);
        version(session, expected);
        if (!session.getStatus().equals("COMPLETED")) {
            // A failed answer was not committed: restore the saved question and let the user answer
            // it again.
            if (session.getQuestionJson() != null) {
                session.apply(
                        session.getSlotsJson(),
                        session.getVisionJson(),
                        session.getQuestionJson(),
                        null,
                        session.getTurn());
            } else advance(session, null);
        } else {
            var result = read(session.getResultJson());
            if (!Boolean.TRUE.equals(result.get("guide_available"))) {
                addGuide(result, read(session.getSlotsJson()));
                session.apply(
                        session.getSlotsJson(),
                        session.getVisionJson(),
                        null,
                        write(result),
                        session.getTurn());
            }
        }
        sessions.saveAndFlush(session);
        return view(session, true);
    }

    @Transactional
    public Map<String, Object> progress(
            String user, String id, skincare.dto.AnalysisProgressRequest body) {
        if (body == null || body.expectedVersion() == null || body.expectedVersion() < 0)
            throw bad("기록 버전이 필요합니다.");
        Integer pain = null;
        if (body.painScore() != null) {
            try {
                pain = body.painScore().intValueExact();
            } catch (ArithmeticException e) {
                throw bad("통증은 0~10 사이 정수로 입력해 주세요.");
            }
            if (pain < 0 || pain > 10) throw bad("통증은 0~10 사이 정수로 입력해 주세요.");
        }
        String note = body.note() == null ? "" : body.note().strip();
        if (note.length() > 2000) throw bad("경과 메모는 2000자 이하여야 합니다.");
        var session = owned(user, id, true);
        var capturedOn =
                body.capturedOn() == null
                        ? session.getCapturedOn()
                        : captureDate(body.capturedOn());
        String swelling = symptom(body.swelling(), session.getSwelling());
        String exudate = symptom(body.exudate(), session.getExudate());
        // A response-lost retry is safe only while the current saved values still match.
        if (Objects.equals(pain, session.getPainScore())
                && Objects.equals(capturedOn, session.getCapturedOn())
                && Objects.equals(swelling, session.getSwelling())
                && Objects.equals(exudate, session.getExudate())
                && note.equals(Objects.requireNonNullElse(session.getProgressNote(), "")))
            return view(session, true);
        version(session, body.expectedVersion());
        session.recordProgress(pain, note);
        session.recordCapture(capturedOn);
        session.recordSymptoms(swelling, exudate);
        sessions.saveAndFlush(session);
        return view(session, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> comparison(String user, String id, String referenceId) {
        var current = owned(user, id, false);
        var reference =
                referenceId == null || referenceId.isBlank()
                        ? sessions.findFirstByUserIdAndCaseIdOrderByCreatedAtAscIdAsc(
                                        user, current.getCaseId())
                                .orElseThrow()
                        : owned(user, referenceId, false);
        if (!current.getCaseId().equals(reference.getCaseId())) throw bad("같은 상처의 기록만 비교할 수 있습니다.");
        return Map.of(
                "current",
                view(current, false),
                "reference",
                view(reference, false),
                "sameRecord",
                current.getId().equals(reference.getId()),
                "recordCount",
                sessions.countByUserIdAndCaseId(user, current.getCaseId()));
    }

    @Transactional
    public void delete(String user, String id) {
        var session = owned(user, id, true);
        jdbc.update(
                "INSERT INTO analysis_erasure_receipts(id,session_id,requested_at) VALUES"
                        + " (?,?,CURRENT_TIMESTAMP)",
                java.util.UUID.randomUUID().toString(),
                id);
        answers.deleteBySessionId(id);
        sessions.delete(session);
    }

    private Map<String, Object> vision(Map<String, Object> raw) {
        var classification = raw.get("classification") instanceof Map<?, ?> map ? map : Map.of();
        var quality = raw.get("quality") instanceof Map<?, ?> map ? map : Map.of();
        Object label = classification.get("label");
        boolean passed =
                Boolean.TRUE.equals(quality.get("ok"))
                        && label instanceof String
                        && Set.of("abrasion", "bruise", "burn", "open_wound", "normal_skin")
                                .contains(label)
                        && classification.get("gate_fail") instanceof List<?> fails
                        && fails.isEmpty();
        var result = new LinkedHashMap<String, Object>();
        result.put("gate_passed", passed);
        result.put("label", passed ? label : "UNKNOWN");
        result.put("display", classification.get("display"));
        return result;
    }

    private boolean advance(AnalysisSession session, AnalysisAnswerRequest answer) {
        try {
            var rawVision =
                    session.getVisionJson() == null
                            ? ai.predict(session.getImage(), session.getImageType())
                            : read(session.getVisionJson());
            if (!(rawVision.get("quality") instanceof Map<?, ?>))
                throw new AiUnavailableException();
            var body = new LinkedHashMap<String, Object>();
            body.put("session_id", session.getId());
            body.put("slots", read(session.getSlotsJson()));
            body.put("vision", vision(rawVision));
            int turn = session.getTurn() + (answer == null ? 0 : 1);
            body.put("turn", turn);
            body.put("user_text", answer == null ? "" : answer.userText());
            if (answer != null) {
                body.put("last_question_id", answer.questionId());
                body.put("answer", answer.answer());
            }
            var next = ai.question(body);
            var slots = object(next.get("slots"));
            if ("ask".equals(next.get("action"))) {
                if (!(next.get("question_id") instanceof String)
                        || !(next.get("utterance") instanceof String)
                        || !(next.get("choices") instanceof List<?>))
                    throw new AiUnavailableException();
                session.apply(write(slots), write(rawVision), write(next), null, turn);
            } else if ("finish".equals(next.get("action"))) {
                var analysisBody = new LinkedHashMap<String, Object>();
                analysisBody.put("session_id", session.getId());
                analysisBody.put("slots", slots);
                analysisBody.put("vision", vision(rawVision));
                // A rule-engine emergency must never wait for a second analysis request.
                var decision =
                        "EMERGENCY".equals(next.get("level")) ? next : ai.analyze(analysisBody);
                var result = new LinkedHashMap<String, Object>();
                Object level = decision.get("level");
                if (level == null
                        || !Set.of("OBSERVATION", "WARNING", "EMERGENCY", "UNKNOWN")
                                .contains(level)) throw new AiUnavailableException();
                result.put("level", level);
                result.put("rule_ids", decision.getOrDefault("rule_ids", List.of()));
                result.put("missing_slots", decision.getOrDefault("missing_slots", List.of()));
                if ("EMERGENCY".equals(level)) {
                    result.put("guide", Map.of("cards", List.of(), "reason", "emergency_priority"));
                    result.put("guide_available", false);
                } else addGuide(result, slots);
                session.apply(write(slots), write(rawVision), null, write(result), turn);
            } else throw new AiUnavailableException();
            return true;
        } catch (AiUnavailableException e) {
            session.fail("AI_UNAVAILABLE");
            return false;
        }
    }

    private void addGuide(Map<String, Object> result, Map<String, Object> slots) {
        try {
            var request = new LinkedHashMap<String, Object>();
            request.put("level", result.get("level"));
            request.put("rule_ids", result.get("rule_ids"));
            request.put("slots", slots);
            var guide = ai.guide(request);
            result.put("guide", guide);
            result.put("guide_available", true);
        } catch (AiUnavailableException | skincare.exception.CustomException e) {
            result.put("guide", Map.of("cards", List.of(), "reason", "guide_unavailable"));
            result.put("guide_available", false);
        }
    }

    private Map<String, Object> view(AnalysisSession session, boolean detail) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", session.getId());
        result.put("caseId", session.getCaseId());
        result.put("status", session.getStatus());
        result.put("version", session.getVersion());
        result.put("turn", session.getTurn());
        result.put("createdAt", session.getCreatedAt());
        result.put("updatedAt", session.getUpdatedAt());
        result.put("imagePath", "/api/wc/analyses/" + session.getId() + "/image");
        result.put("errorCode", session.getErrorCode());
        result.put("sourceKind", session.getSourceKind());
        result.put("trainingEligible", session.isTrainingEligible());
        result.put("capturedOn", session.getCapturedOn());
        result.put("trainingReviewStatus", session.getTrainingReviewStatus());
        var progress = new LinkedHashMap<String, Object>();
        progress.put("painScore", session.getPainScore());
        progress.put("swelling", session.getSwelling());
        progress.put("exudate", session.getExudate());
        progress.put("note", Objects.requireNonNullElse(session.getProgressNote(), ""));
        result.put("progress", progress);
        var evaluation = read(session.getResultJson());
        result.put("level", evaluation.get("level"));
        if (detail) {
            result.put("slots", read(session.getSlotsJson()));
            result.put("vision", read(session.getVisionJson()));
            result.put(
                    "question",
                    session.getQuestionJson() == null ? null : read(session.getQuestionJson()));
            result.put("result", session.getResultJson() == null ? null : evaluation);
            result.put(
                    "answers",
                    answers.findBySessionIdOrderByTurnAsc(session.getId()).stream()
                            .map(
                                    a -> {
                                        var item = new LinkedHashMap<String, Object>();
                                        item.put("questionId", a.getQuestionId());
                                        item.put("answer", a.getAnswer());
                                        item.put("userText", a.getUserText());
                                        item.put("questionText", a.getQuestionText());
                                        item.put("answerLabel", a.getAnswerLabel());
                                        item.put("turn", a.getTurn());
                                        item.put("createdAt", a.getCreatedAt());
                                        return item;
                                    })
                            .toList());
        }
        return result;
    }
}
