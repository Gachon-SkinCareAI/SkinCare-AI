package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.hospital.entity.Hospital;
import skincare.dto.HospitalResponseDto;
import skincare.dto.HospitalSearchConditionDto;
import skincare.dto.Paging;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.HospitalRepository;

import java.util.Comparator;
import java.util.List;

// 사용자용 의료기관(병원/약국) 검색 및 조회 관련 비즈니스 로직 처리
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// HospitalQueryService 비즈니스 로직 처리
public class HospitalQueryService {

    private static final double DEFAULT_RADIUS_KM = 3.0; // 기본 검색 반경
    private static final double MAX_RADIUS_KM = 30.0; // 최대 검색 반경 (과도한 조회 방지)
    private static final double EARTH_RADIUS_KM = 6371.0; // 지구 반지름

    private final HospitalRepository hospitalRepository; // 의료기관 데이터 베이스 접근용 Repository

    /**
     * 위치 및 조건별 피부과/약국 목록 검색 - 위도/경도가 있으면: 반경 내 기관을 거리순으로 정렬하여 페이지 처리 - 위도/경도가 없으면: 조건 검색 후 기관명순 페이지
     * 처리
     */
    public Page<HospitalResponseDto> searchHospitals(HospitalSearchConditionDto condition) {
        boolean locationSearch =
                condition.getLatitude() != null && condition.getLongitude() != null;

        if (!locationSearch) {
            Specification<Hospital> spec = buildSpec(condition, null);
            Page<Hospital> hospitals =
                    hospitalRepository.findAll(
                            spec,
                            Paging.of(condition.getPage(), condition.getSize(), Sort.by("name")));
            return hospitals.map(hospital -> HospitalResponseDto.summary(hospital, null));
        }

        // 반경 검색: 위도/경도 사각 범위로 1차 조회 후 실제 거리(Haversine)로 반경 필터 및 거리순 정렬
        double lat = condition.getLatitude();
        double lng = condition.getLongitude();
        double radiusKm = resolveRadius(condition.getRadiusKm());
        double latDelta = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);
        double lngDelta =
                Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * Math.cos(Math.toRadians(lat))));
        double[] bounds = {lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta};

        List<HospitalResponseDto> nearby =
                hospitalRepository.findAll(buildSpec(condition, bounds)).stream()
                        .map(
                                hospital ->
                                        HospitalResponseDto.summary(
                                                hospital,
                                                distanceKm(
                                                        lat,
                                                        lng,
                                                        hospital.getLatitude(),
                                                        hospital.getLongitude())))
                        .filter(dto -> dto.getDistanceKm() <= radiusKm)
                        .sorted(Comparator.comparingDouble(HospitalResponseDto::getDistanceKm))
                        .toList();

        Pageable pageable = Paging.of(condition.getPage(), condition.getSize());
        int fromIndex = (int) Math.min(pageable.getOffset(), nearby.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), nearby.size());
        return new PageImpl<>(nearby.subList(fromIndex, toIndex), pageable, nearby.size());
    }

    // 의료기관 상세 정보 조회 (진료시간, 출처, 갱신일 포함)
    public HospitalResponseDto getHospitalDetail(String hospitalId) {
        // 관리자가 숨김 처리한 기관은 사용자에게 노출하지 않음
        Hospital hospital =
                hospitalRepository
                        .findByHospitalId(hospitalId)
                        .filter(found -> !Hospital.STATUS_HIDDEN.equals(found.getStatus()))
                        .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));

        return HospitalResponseDto.detail(hospital);
    }

    // 검색 조건 Specification 생성
    private Specification<Hospital> buildSpec(
            HospitalSearchConditionDto condition, double[] bounds) {
        return HospitalRepository.buildSearchSpecification(
                condition.getKeyword(),
                condition.getRegion(),
                condition.getHospitalType(),
                condition.getIsNightCare(),
                condition.getIsEmergency(),
                bounds);
    }

    // 검색 반경 보정 (미입력/0 이하: 기본 3km, 최대 30km)
    private double resolveRadius(Double radiusKm) {
        if (radiusKm == null || radiusKm <= 0) {
            return DEFAULT_RADIUS_KM;
        }
        return Math.min(radiusKm, MAX_RADIUS_KM);
    }

    // 두 좌표 사이의 거리 계산 (Haversine 공식, km)
    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLng / 2)
                                * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
