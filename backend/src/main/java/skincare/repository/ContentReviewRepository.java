package skincare.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import skincare.domain.admin.entity.ContentReview;

import java.util.Optional;

// ContentReviewRepository 데이터 접근 처리
public interface ContentReviewRepository extends JpaRepository<ContentReview, String> {
    Page<ContentReview> findByStatus(String status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContentReview r where r.id=:id")
    Optional<ContentReview> lockById(String id);
}
