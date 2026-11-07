package skincare.dto;
public record AnalysisProgressRequest(Long expectedVersion, java.math.BigDecimal painScore, String note,
        String capturedOn, String swelling, String exudate) {}
