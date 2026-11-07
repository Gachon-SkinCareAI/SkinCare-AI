package skincare.repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 의료기관(Hospital) 엔티티에 대한 데이터베이스 접근 인터페이스
@Repository
public interface HospitalRepository extends JpaRepository<Hospital, String>, JpaSpecificationExecutor<Hospital> {
    // 기관 고유 ID로 의료기관 단건 조회
    Optional<Hospital> findByHospitalId(String hospitalId);

    // 운영 상태별 기관 수 (관리자 의료기관 통계)
    long countByStatus(String status);

    // 운영 중인 야간진료 병원 수 (약국 제외)
    long countByStatusAndNightCareTrueAndHospitalTypeNot(String status, HospitalType hospitalType);

    // 운영 중인 야간 운영 약국 수
    long countByStatusAndNightCareTrueAndHospitalType(String status, HospitalType hospitalType);

    // 운영 중인 응급실 운영 기관 수
    long countByStatusAndEmergencyTrue(String status);

    // 관리자용 의료기관 검색 조건 (검색어: 기관명/주소/기관 ID, 기관 유형, 운영 상태 - 운영 중지/숨김 기관 포함)
    static Specification<Hospital> adminSearch(String keyword, HospitalType hospitalType, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("address"), like),
                        cb.like(root.get("hospitalId"), like)
                ));
            }
            if (hospitalType != null) {
                predicates.add(cb.equal(root.get("hospitalType"), hospitalType));
            }
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Dynamic Specification 검색을 처리하는 Query Specification Builder
    // bounds가 있으면 위도/경도 사각 범위(반경 검색 1차 필터) 조건 추가 [최소위도, 최대위도, 최소경도, 최대경도]
    static Specification<Hospital> buildSearchSpecification(
            String keyword, String region, HospitalType hospitalType,
            Boolean isNightCare, Boolean isEmergency, double[] bounds) {

        return (root, query, cb) -> {
            // 검색 조건 목록 (마지막에 AND로 결합)
            List<Predicate> predicates = new ArrayList<>();

            // 1. 키워드 검색 (병원/약국 이름 또는 주소)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likeKeyword = "%" + keyword.trim() + "%";
                predicates.add(
                        cb.or(
                                cb.like(root.get("name"), likeKeyword),
                                cb.like(root.get("address"), likeKeyword)
                        )
                );
            }

            // 2. 지역 검색 (ex. "강남구")
            if (region != null && !region.trim().isEmpty()) {
                predicates.add(
                        cb.like(root.get("region"), "%" + region.trim() + "%")
                );
            }

            // 3. 병원/약국 카테고리 필터
            if (hospitalType != null) {
                predicates.add(
                        cb.equal(root.get("hospitalType"), hospitalType)
                );
            }

            // 4. 야간진료 여부 필터
            if (Boolean.TRUE.equals(isNightCare)) {
                predicates.add(
                        cb.equal(root.get("nightCare"), true)
                );
            }

            // 5. 응급실 여부 필터
            if (Boolean.TRUE.equals(isEmergency)) {
                predicates.add(
                        cb.equal(root.get("emergency"), true)
                );
            }

            // 6. 위치 범위 필터 (좌표가 있는 기관 중 사각 범위 내)
            if (bounds != null) {
                predicates.add(cb.between(root.get("latitude"), bounds[0], bounds[1]));
                predicates.add(cb.between(root.get("longitude"), bounds[2], bounds[3]));
            }

            // 7. 정상 영업(ACTIVE) 중인 곳만 조회
            predicates.add(
                    cb.equal(root.get("status"), "ACTIVE")
            );

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select h from Hospital h where h.hospitalId=:id")
 Optional<Hospital> lockById(@Param("id") String id);
}
