package skincare.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import skincare.domain.user.entity.ConsentEvent;

// ConsentEventRepository 데이터 접근 처리
public interface ConsentEventRepository extends JpaRepository<ConsentEvent, String> {
    Page<ConsentEvent> findByUserId(String userId, Pageable page);
}
