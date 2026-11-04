package skincare.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import skincare.domain.user.entity.ConsentEvent;
public interface ConsentEventRepository extends JpaRepository<ConsentEvent,String> {
    Page<ConsentEvent> findByUserId(String userId,Pageable page);
}
