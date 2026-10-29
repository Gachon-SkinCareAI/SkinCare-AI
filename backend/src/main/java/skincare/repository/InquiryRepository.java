package skincare.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.Inquiry;

import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, String> {

    // 1. 관리자용 전체 문의 목록 조회 (페이지네이션)
    Page<Inquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 2. 관리자용 상태별(WAITING / COMPLETED) 문의 목록 조회 (페이지네이션)
    Page<Inquiry> findByStatusOrderByCreatedAtDesc(InquiryStatus status, Pageable pageable);

    // 3. 관리자용 문의 상세 조회 (Fetch Join으로 연관 엔티티 한 번에 조회)
    @Query("SELECT i FROM Inquiry i " +
            "LEFT JOIN FETCH i.user " +
            "LEFT JOIN FETCH i.diagnosis " +
            "LEFT JOIN FETCH i.answer " +
            "WHERE i.inquiryId = :inquiryId")
    Optional<Inquiry> findDetailByInquiryId(@Param("inquiryId") String inquiryId);

    // 특정 사용자가 작성한 문의 목록 조회 (페이지네이션, 최신순)
    Page<Inquiry> findByUserUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // 특정 사용자의 문의 상세 조회 (본인 문의 확인 및 Fetch Join)
    @Query("SELECT i FROM Inquiry i " +
            "LEFT JOIN FETCH i.diagnosis " +
            "LEFT JOIN FETCH i.answer " +
            "WHERE i.inquiryId = :inquiryId AND i.user.userId = :userId")
    Optional<Inquiry> findByInquiryIdAndUserId(@Param("inquiryId") String inquiryId, @Param("userId") String userId);
}