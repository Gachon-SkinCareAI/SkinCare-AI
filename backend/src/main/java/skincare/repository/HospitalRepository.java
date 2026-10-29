package skincare.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import skincare.domain.hospital.entity.Hospital;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, String>, JpaSpecificationExecutor<Hospital> {
    Optional<Hospital> findByHospitalId(String hospitalId);

    // 전체 ID 목록만 빠르게 수집하는 쿼리 추가
    @Query("SELECT h.hospitalId FROM Hospital h")
    List<String> findAllIds();

    // Dynamic Specification 검색을 처리하는 Query Specification Builder
    static Specification<Hospital> buildSearchSpecification(
            String keyword, String region, skincare.domain.hospital.entity.ENUM.HospitalType hospitalType,
            Boolean isNightCare, Boolean isEmergency) {

        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            // 1. 키워드 검색 (병원/약국 이름 또는 주소)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likeKeyword = "%" + keyword.trim() + "%";
                predicates.getExpressions().add(
                        cb.or(
                                cb.like(root.get("name"), likeKeyword),
                                cb.like(root.get("address"), likeKeyword)
                        )
                );
            }

            // 2. 지역 검색 (ex. "강남구")
            if (region != null && !region.trim().isEmpty()) {
                predicates.getExpressions().add(
                        cb.like(root.get("region"), "%" + region.trim() + "%")
                );
            }

            // 3. 병원/약국 카테고리 필터
            if (hospitalType != null) {
                predicates.getExpressions().add(
                        cb.equal(root.get("hospitalType"), hospitalType)
                );
            }

            // 4. 야간진료 여부 필터
            if (Boolean.TRUE.equals(isNightCare)) {
                predicates.getExpressions().add(
                        cb.equal(root.get("nightCare"), true)
                );
            }

            // 5. 응급실 여부 필터
            if (Boolean.TRUE.equals(isEmergency)) {
                predicates.getExpressions().add(
                        cb.equal(root.get("emergency"), true)
                );
            }

            // 6. 정상 영업(ACTIVE) 중인 곳만 조회
            predicates.getExpressions().add(
                    cb.equal(root.get("status"), "ACTIVE")
            );

            return predicates;
        };
    }
}
