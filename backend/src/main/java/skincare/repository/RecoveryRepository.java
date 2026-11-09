package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import skincare.domain.diagnosis.entity.Recovery;

import java.util.List;
import java.util.Optional;

// 회복 경과 기록(Recovery) 엔티티에 대한 데이터베이스 접근 인터페이스
// RecoveryRepository 데이터 접근 처리
public interface RecoveryRepository extends JpaRepository<Recovery, String> {

    // 특정 분석 기록의 회복 경과 기록 목록 (최신순)
    List<Recovery> findByDiagnosisDiagnosisIdOrderByCreatedAtDesc(String diagnosisId);

    // 사용자의 전체 회복 기록 (분석 기록 Fetch Join, 최신순) - 상처별 묶음 구성용
    @Query(
            "SELECT r FROM Recovery r JOIN FETCH r.diagnosis d "
                    + "WHERE d.user.userId = :userId ORDER BY r.createdAt DESC")
    List<Recovery> findAllByUserId(@Param("userId") String userId);

    // 본인 회복 기록 단건 조회 (분석 기록 소유자 검증)
    @Query(
            "SELECT r FROM Recovery r JOIN FETCH r.diagnosis d "
                    + "WHERE r.recoveryId = :recoveryId AND d.user.userId = :userId")
    Optional<Recovery> findByRecoveryIdAndUserId(
            @Param("recoveryId") String recoveryId, @Param("userId") String userId);

    // 사용자의 전체 회복 기록 수
    long countByDiagnosisUserUserId(String userId);

    // 사용자의 최근 회복 기록 (최근 활동 표시용)
    List<Recovery> findTop5ByDiagnosisUserUserIdOrderByCreatedAtDesc(String userId);
}
