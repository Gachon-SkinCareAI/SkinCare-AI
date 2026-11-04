package skincare.repository;
import skincare.domain.inquiry.entity.InquiryAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer,String> {
    Optional<InquiryAnswer> findByInquiry_InquiryId(String id);
}
