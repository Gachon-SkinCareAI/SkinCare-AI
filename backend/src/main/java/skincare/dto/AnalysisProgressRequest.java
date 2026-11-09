package skincare.dto;

// AnalysisProgressRequest 요청과 응답 데이터
public record AnalysisProgressRequest(
        Long expectedVersion,
        java.math.BigDecimal painScore,
        String note,
        String capturedOn,
        String swelling,
        String exudate) {}
