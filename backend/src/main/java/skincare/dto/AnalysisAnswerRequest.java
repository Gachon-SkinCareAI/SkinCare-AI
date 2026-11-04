package skincare.dto;

public record AnalysisAnswerRequest(String clientRequestId, Long expectedVersion, String questionId,
                                    String answer, String userText) {}
