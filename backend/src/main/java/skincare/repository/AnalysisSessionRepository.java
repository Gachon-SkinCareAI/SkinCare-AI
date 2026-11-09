package skincare.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import skincare.domain.analysis.AnalysisSession;

import java.util.List;
import java.util.Optional;

// AnalysisSessionRepository 데이터 접근 처리
public interface AnalysisSessionRepository extends JpaRepository<AnalysisSession, String> {
    Optional<AnalysisSession> findByIdAndUserId(String id, String userId);

    Optional<AnalysisSession> findFirstByUserIdAndCaseIdOrderByCreatedAtAscIdAsc(
            String userId, String caseId);

    long countByUserIdAndCreatedAtAfter(String userId, java.time.LocalDateTime since);

    long countByUserIdAndCaseId(String userId, String caseId);

    interface StateCount {
        String getStatus();

        long getTotal();
    }

    @Query("select s.status as status, count(s) as total from AnalysisSession s group by s.status")
    List<StateCount> countStates();

    @Query(
            "select s.id as id, s.status as status, s.turn as turn, s.errorCode as errorCode,"
                + " s.createdAt as createdAt, s.updatedAt as updatedAt from AnalysisSession s where"
                + " (:status is null or s.status = :status) order by s.createdAt desc, s.id desc")
    org.springframework.data.domain.Page<AnalysisOperationSummary> operations(
            @Param("status") String status, Pageable page);

    Optional<AnalysisSession> findByUserIdAndClientRequestId(String userId, String clientRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AnalysisSession s where s.id = :id and s.userId = :owner")
    Optional<AnalysisSession> lockOwned(@Param("id") String id, @Param("owner") String userId);

    @Query(
            "select s.id as id, s.caseId as caseId, s.status as status, s.version as version,"
                + " s.turn as turn, s.createdAt as createdAt, s.capturedOn as capturedOn,"
                + " s.updatedAt as updatedAt, s.errorCode as errorCode, s.resultJson as resultJson"
                + " from AnalysisSession s where s.userId = :owner and (:caseId is null or s.caseId"
                + " = :caseId) order by s.createdAt desc, s.id desc")
    List<AnalysisSummary> listOwned(
            @Param("owner") String userId, @Param("caseId") String caseId, Pageable page);
}
