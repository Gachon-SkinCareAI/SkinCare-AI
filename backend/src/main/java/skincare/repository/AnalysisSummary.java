package skincare.repository;
import java.time.LocalDateTime;
public interface AnalysisSummary {
    String getId(); String getCaseId(); String getStatus(); long getVersion(); int getTurn();
    LocalDateTime getCreatedAt(); LocalDateTime getUpdatedAt(); String getErrorCode(); String getResultJson();
    java.time.LocalDate getCapturedOn();
}
