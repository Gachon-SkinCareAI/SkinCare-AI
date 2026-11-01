package skincare.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skincare.domain.diagnosis.entity.Diagnosis;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// AI 분석 기록(Diagnosis) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface DiagnosisRepository extends JpaRepository<Diagnosis, String>, JpaSpecificationExecutor<Diagnosis> {

    // 특정 분석 ID 및 사용자 ID로 분석 기록 단건 조회 (본인 분석 기록 검증용)
    @Query("SELECT d FROM Diagnosis d " +
            "JOIN FETCH d.user " +
            "WHERE d.diagnosisId = :diagnosisId AND d.user.userId = :userId")
    Optional<Diagnosis> findByDiagnosisIdAndUserId(@Param("diagnosisId") String diagnosisId, @Param("userId") String userId);

    // 특정 사용자의 전체 AI 분석 기록 최신순 목록 조회 (페이지네이션)
    Page<Diagnosis> findByUserUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // 특정 사용자의 위험도별 AI 분석 기록 최신순 목록 조회 (페이지네이션)
    Page<Diagnosis> findByUserUserIdAndRiskLevelOrderByCreatedAtDesc(String userId, RiskLevel riskLevel, Pageable pageable);

    // 관리자용 분석건 상세 조회 (User 및 Metrics Fetch Join)
    @Query("SELECT d FROM Diagnosis d " +
            "JOIN FETCH d.user " +
            "LEFT JOIN FETCH d.metrics " +
            "WHERE d.diagnosisId = :diagnosisId")
    Optional<Diagnosis> findDetailByDiagnosisId(@Param("diagnosisId") String diagnosisId);

    // 관리자용 분석 로그 검색 조건 (위험도, 처리 상태, 검색어: 분석 ID/사용자 ID/이메일/닉네임)
    static Specification<Diagnosis> adminSearch(RiskLevel riskLevel, DiagnosisStatus status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            var user = root.join("user", JoinType.INNER);

            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("diagnosisId"), like),
                        cb.like(user.get("userId"), like),
                        cb.like(user.get("email"), like),
                        cb.like(user.get("nickname"), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
