package skincare.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skincare.domain.diagnosis.entity.Diagnosis;

import java.util.Optional;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    // 특정 진단 ID 및 사용자 ID로 진단 기록 단건 조회 (본인 진단 기록 검증용)
    @Query("SELECT d FROM Diagnosis d " +
            "JOIN FETCH d.user " +
            "WHERE d.diagnosisId = :diagnosisId AND d.user.userId = :userId")
    Optional<Diagnosis> findByDiagnosisIdAndUserId(@Param("diagnosisId") Long diagnosisId, @Param("userId") Long userId);

    // 특정 사용자의 전체 AI 진단 기록 최신순 목록 조회 (페이지네이션)
    Page<Diagnosis> findByUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}