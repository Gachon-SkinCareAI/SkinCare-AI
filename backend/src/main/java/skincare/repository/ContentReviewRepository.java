package skincare.repository;
import skincare.domain.admin.entity.ContentReview;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ContentReviewRepository extends JpaRepository<ContentReview,String> {
    Page<ContentReview> findByStatus(String status,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ContentReview r where r.id=:id")
    Optional<ContentReview> lockById(String id);
}
