package skincare.dto;

// AnalysisAnswerRequest 요청과 응답 데이터
public record AnalysisAnswerRequest(
        String clientRequestId,
        Long expectedVersion,
        String questionId,
        String answer,
        String userText) {}
