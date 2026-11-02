package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.user.entity.UserChangeHistory;

import java.util.List;

// 회원 변경 이력(UserChangeHistory) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface UserChangeHistoryRepository extends JpaRepository<UserChangeHistory, Long> {

    // 회원별 변경 이력 (최신순)
    List<UserChangeHistory> findByUserIdOrderByCreatedAtDesc(String userId);
}
