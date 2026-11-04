package skincare.repository;
import skincare.domain.inquiry.entity.Inquiry;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface InquiryRepository extends JpaRepository<Inquiry,String> {
    Optional<Inquiry> findByInquiryIdAndUser_UserId(String id,String owner);
    Optional<Inquiry> findByUser_UserIdAndClientRequestId(String owner,String clientRequestId);
    Page<Inquiry> findByUser_UserId(String owner,Pageable page);
    @Query("select i from Inquiry i where (:status is null or i.status=:status)")
    Page<Inquiry> listAdmin(@Param("status") InquiryStatus status,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inquiry i where i.inquiryId=:id")
    Optional<Inquiry> lockById(@Param("id") String id);
    long countByStatus(InquiryStatus status);
}
