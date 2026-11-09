package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import skincare.domain.inquiry.entity.InquiryAnswer;

import java.util.Optional;

// InquiryAnswerRepository 데이터 접근 처리
public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer, String> {
    Optional<InquiryAnswer> findByInquiry_InquiryId(String id);
}
