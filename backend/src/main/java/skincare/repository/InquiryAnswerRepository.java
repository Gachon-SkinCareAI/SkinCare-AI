package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.inquiry.entity.InquiryAnswer;

import java.util.Optional;

// 문의 답변(InquiryAnswer) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface InquiryAnswerRepository extends JpaRepository<InquiryAnswer, String> {

    // 문의 ID(Inquiry)로 기존 답변 조회
    Optional<InquiryAnswer> findByInquiryInquiryId(String inquiryId);

    // 문의 ID(Inquiry)로 기존 답변 삭제
    void deleteByInquiryInquiryId(String inquiryId);
}