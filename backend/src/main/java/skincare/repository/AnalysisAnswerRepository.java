package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import skincare.domain.analysis.AnalysisAnswer;

import java.util.List;
import java.util.Optional;

// AnalysisAnswerRepository 데이터 접근 처리
public interface AnalysisAnswerRepository extends JpaRepository<AnalysisAnswer, String> {
    List<AnalysisAnswer> findBySessionIdOrderByTurnAsc(String id);

    Optional<AnalysisAnswer> findBySessionIdAndClientRequestId(String id, String requestId);

    void deleteBySessionId(String id);
}
