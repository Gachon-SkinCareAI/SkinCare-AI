package skincare.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;

import java.util.Optional;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, String> {

    // 특정 진단 ID 및 사용자 ID로 진단 기록 단건 조회 (본인 진단 기록 검증용)
    @Query("SELECT d FROM Diagnosis d " +
            "JOIN FETCH d.user " +
            "WHERE d.diagnosisId = :diagnosisId AND d.user.userId = :userId")
    Optional<Diagnosis> findByDiagnosisIdAndUserId(@Param("diagnosisId") String diagnosisId, @Param("userId") String userId);

    // 특정 사용자의 전체 AI 진단 기록 최신순 목록 조회 (페이지네이션)
    Page<Diagnosis> findByUserUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // 관리자용 전체 진단 목록 조회 (페이지네이션)
    Page<Diagnosis> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 관리자용 상태별(PENDING, IN_PROGRESS, COMPLETED, ALERT_SENT) 진단 목록 조회
    Page<Diagnosis> findByStatusOrderByCreatedAtDesc(DiagnosisStatus status, Pageable pageable);

    // 관리자용 진단건 상세 조회 (User 및 Metrics Fetch Join)
    @Query("SELECT d FROM Diagnosis d " +
            "JOIN FETCH d.user " +
            "LEFT JOIN FETCH d.metrics " +
            "WHERE d.diagnosisId = :diagnosisId")
    Optional<Diagnosis> findDetailByDiagnosisId(@Param("diagnosisId") String diagnosisId);
}