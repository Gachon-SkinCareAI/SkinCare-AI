package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.WoundType;
import skincare.dto.CareGuideDto;
import skincare.dto.DiagnosisResponseDto;
import skincare.dto.QuestionnaireAnswerRequestDto;
import skincare.dto.ai.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.external.AiServiceClient;
import skincare.repository.DiagnosisRepository;

import java.util.*;

// 분석 결과 기반 동적 문진 진행 서비스
// 처리 흐름: 사용자 응답 → AI /question(다음 질문 선택, 슬롯 추출) 반복 → 문진 종료 시 /analyze → /guide 로 재평가 후 저장
// - 문진은 분석 직후 제공되는 1차 결과를 보완하는 선택 단계 (앱에서 진행하지 않아도 1차 결과는 유지)
// - 최대 10턴까지 진행하고, 끝까지 확보되지 않은 슬롯은 unknown으로 처리
// - 질문 문구는 LLM이 생성하지 않고 AI 서버의 검증된 Question Bank에서 선택된 문항을 그대로 전달
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireService {

    private static final int MAX_TURN = 10; // 최대 문진 턴

    private final DiagnosisRepository diagnosisRepository; // 분석 기록 데이터 베이스 접근용 Repository
    private final DiagnosisUpdater diagnosisUpdater;       // 문진/재평가 결과 저장
    private final DiagnosisService diagnosisService;       // 케어 카드 조회 공통 로직
    private final AiServiceClient aiServiceClient;         // AI 서버 문진/위험도/가이드 연동
    private final JsonConverter jsonConverter;             // 문진 JSON 변환

    // 문진 응답 제출 및 다음 질문 또는 최종 재평가 결과 반환
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> answer(String userId, String diagnosisId, QuestionnaireAnswerRequestDto dto) {
        Diagnosis diagnosis = diagnosisRepository.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DIAGNOSIS_NOT_FOUND));
        if (!StringUtils.hasText(diagnosis.getAiRequestId())) {
            throw new CustomException(ErrorCode.INVALID_AI_REQUEST, "AI 분석 요청 정보가 없어 문진을 진행할 수 없습니다.");
        }

        Map<String, Object> state = new LinkedHashMap<>(jsonConverter.toMap(diagnosis.getQuestionnaire()));
        if (Boolean.TRUE.equals(state.get("done"))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이미 완료된 문진입니다.");
        }

        Map<String, Object> slots = new LinkedHashMap<>(castMap(state.get("slots")));
        List<Map<String, Object>> history = new ArrayList<>(castList(state.get("history")));
        int turn = state.get("turn") instanceof Number n ? n.intValue() : 0;
        Map<String, Object> pending = castMap(state.get("pending_question"));

        // 직전 질문에 대한 응답 기록 (대기 중인 질문과 다른 질문 ID로 응답하면 거부)
        boolean hasAnswer = StringUtils.hasText(dto.getAnswer());
        if (hasAnswer) {
            if (pending.isEmpty() || !Objects.equals(pending.get("question_id"), dto.getQuestionId())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "현재 진행 중인 질문에 대한 응답이 아닙니다.");
            }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("question_id", dto.getQuestionId());
            record.put("question", pending.get("question"));
            record.put("answer", dto.getAnswer().trim());
            history.add(record);
        }

        // 다음 질문 요청 (응급 규칙 우선 평가는 AI 서버에서 수행)
        WoundType label = diagnosis.getAiLabel() != null ? diagnosis.getAiLabel() : WoundType.UNKNOWN;
        AiQuestionResponseDto next = aiServiceClient.question(new AiQuestionRequestDto(
                diagnosis.getAiRequestId(), label.getCode(), slots,
                hasAnswer ? dto.getQuestionId() : null, hasAnswer ? dto.getAnswer().trim() : null, turn));
        if (next.slots() != null) {
            slots.putAll(next.slots());
        }
        turn = hasAnswer ? turn + 1 : turn;

        state.put("slots", slots);
        state.put("history", history);
        state.put("turn", turn);

        // 문진 종료: AI 판단 종료, 응급 규칙 충족, 최대 턴 도달
        if (next.done() || next.emergency() || turn >= MAX_TURN || !StringUtils.hasText(next.questionId())) {
            return finish(diagnosis, label, state, slots);
        }

        // 다음 질문 대기 상태 저장
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("question_id", next.questionId());
        question.put("question", next.question());
        question.put("options", next.options());
        question.put("turn", turn + 1);
        question.put("max_turn", MAX_TURN);
        state.put("pending_question", question);
        state.put("done", false);
        diagnosisUpdater.saveQuestionnaire(diagnosisId, jsonConverter.toJson(state));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("done", false);
        result.put("next_question", question);
        return result;
    }

    // 문진 종료 처리: 확보된 슬롯으로 위험도 재평가 및 케어 카드 재조회 후 저장
    private Map<String, Object> finish(Diagnosis diagnosis, WoundType label, Map<String, Object> state,
                                       Map<String, Object> slots) {
        AiAnalyzeResponseDto analyze = aiServiceClient.analyze(new AiAnalyzeRequestDto(
                diagnosis.getAiRequestId(), label.getCode(), diagnosis.getConfidenceScore() / 100.0,
                Boolean.TRUE.equals(diagnosis.getGatePassed()), slots));
        AiGuideResponseDto guide = diagnosisService.fetchGuide(analyze, slots);

        List<AiGuideResponseDto.CareCard> cards = guide.cards() != null ? guide.cards() : List.of();
        List<AiGuideResponseDto.AvoidItem> avoid = guide.avoid() != null ? guide.avoid() : List.of();

        state.put("missing_slots", analyze.missingSlots() != null ? analyze.missingSlots() : List.of());
        state.remove("pending_question");
        state.put("done", true);

        Diagnosis updated = diagnosisUpdater.saveReassessment(diagnosis.getDiagnosisId(), jsonConverter.toJson(state),
                analyze.level(), DiagnosisService.joinOrNull(analyze.ruleIds()),
                DiagnosisService.joinOrNull(cards.stream().map(AiGuideResponseDto.CareCard::id).toList()),
                jsonConverter.toJson(guide), CareGuideDto.joinFirstAid(cards), CareGuideDto.joinPrecautions(avoid));

        log.info("[Analysis] 정상 처리, 문진 완료 및 위험도 재평가 (diagnosis_id: {}, risk_level: {}, turn: {})",
                diagnosis.getDiagnosisId(), analyze.level(), state.get("turn"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("done", true);
        result.put("diagnosis_result", DiagnosisResponseDto.detail(updated, CareGuideDto.from(guide)));
        return result;
    }

    // JSON에서 읽은 객체를 Map으로 변환 (없으면 빈 Map)
    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    // JSON에서 읽은 객체를 List로 변환 (없으면 빈 List)
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
