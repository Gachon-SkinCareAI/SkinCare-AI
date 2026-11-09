package skincare.repository;

import java.time.LocalDateTime;

// No user identity, photo, questionnaire, result JSON or progress notes in operations queries.
// AnalysisOperationSummary 데이터 접근 처리
public interface AnalysisOperationSummary {
    String getId();

    String getStatus();

    int getTurn();

    String getErrorCode();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
