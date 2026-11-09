package skincare.repository;

import java.time.LocalDateTime;

// AnalysisSummary 데이터 접근 처리
public interface AnalysisSummary {
    String getId();

    String getCaseId();

    String getStatus();

    long getVersion();

    int getTurn();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    String getErrorCode();

    String getResultJson();

    java.time.LocalDate getCapturedOn();
}
