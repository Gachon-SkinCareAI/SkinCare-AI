package skincare.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.domain.inquiry.entity.ENUM.InquiryType;
import skincare.domain.inquiry.entity.Inquiry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 1:1 문의(Inquiry) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface InquiryRepository extends JpaRepository<Inquiry, String>, JpaSpecificationExecutor<Inquiry> {

    // 관리자용 문의 상세 조회 (Fetch Join으로 연관 엔티티 한 번에 조회)
    @Query("SELECT i FROM Inquiry i " +
            "LEFT JOIN FETCH i.user " +
            "LEFT JOIN FETCH i.diagnosis " +
            "LEFT JOIN FETCH i.answer " +
            "WHERE i.inquiryId = :inquiryId")
    Optional<Inquiry> findDetailByInquiryId(@Param("inquiryId") String inquiryId);

    // 특정 사용자가 작성한 문의 목록 조회 (페이지네이션, 최신순)
    Page<Inquiry> findByUserUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // 특정 사용자의 답변 상태별 문의 목록 조회 (페이지네이션, 최신순)
    Page<Inquiry> findByUserUserIdAndStatusOrderByCreatedAtDesc(String userId, InquiryStatus status, Pageable pageable);

    // 특정 사용자의 문의 상세 조회 (본인 문의 확인 및 Fetch Join)
    @Query("SELECT i FROM Inquiry i " +
            "LEFT JOIN FETCH i.diagnosis " +
            "LEFT JOIN FETCH i.answer " +
            "WHERE i.inquiryId = :inquiryId AND i.user.userId = :userId")
    Optional<Inquiry> findByInquiryIdAndUserId(@Param("inquiryId") String inquiryId, @Param("userId") String userId);

    // 답변 상태별 문의 건수 (관리자 문의 통계)
    long countByStatus(InquiryStatus status);

    // 관리자용 문의 검색 조건 (답변 상태, 문의 유형)
    static Specification<Inquiry> adminSearch(InquiryStatus status, InquiryType inquiryType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (inquiryType != null) {
                predicates.add(cb.equal(root.get("inquiryType"), inquiryType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
