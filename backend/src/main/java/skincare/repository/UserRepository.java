package skincare.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import skincare.domain.user.entity.ENUM.PlanType;
import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 사용자(User) 엔티티에 대한 데이터베이스 접근 인터페이스
@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    // 이메일로 사용자 조회
    Optional<User> findByEmail(String email);

    // 기간 내 가입 회원 수 (대시보드 신규 가입 통계)
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 기준 시각 이전 가입 회원 수 (증가율 계산 기준)
    long countByCreatedAtBefore(LocalDateTime dateTime);

    // 기준 시각 이후 접속 회원 수 (DAU 등 활성 사용자 통계)
    long countByLastAccessAtAfter(LocalDateTime dateTime);

    // 이용 플랜별 회원 수 (Premium 전환 통계)
    long countByPlan(PlanType plan);

    // 계정 상태별 회원 수
    long countByStatus(UserStatus status);

    // 관리자용 회원 검색 조건 (검색어: 회원 ID/이메일/닉네임, 이용 플랜, 계정 상태)
    static Specification<User> adminSearch(String keyword, PlanType plan, UserStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("userId"), like),
                        cb.like(root.get("email"), like),
                        cb.like(root.get("nickname"), like)
                ));
            }
            if (plan != null) {
                predicates.add(cb.equal(root.get("plan"), plan));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
