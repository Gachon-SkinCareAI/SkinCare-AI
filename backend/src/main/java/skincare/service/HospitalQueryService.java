package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.hospital.entity.Hospital;
import skincare.dto.HospitalResponseDto;
import skincare.dto.HospitalSearchConditionDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.HospitalRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalQueryService {

    private final HospitalRepository hospitalRepository;

    /**
     * 1. 위치 및 조건별 피부과/약국 목록 검색
     */
    public Page<HospitalResponseDto> searchHospitals(HospitalSearchConditionDto condition) {
        Pageable pageable = PageRequest.of(condition.getPage(), condition.getSize());

        Specification<Hospital> spec = HospitalRepository.buildSearchSpecification(
                condition.getKeyword(),
                condition.getRegion(),
                condition.getHospitalType(),
                condition.getIsNightCare(),
                condition.getIsEmergency()
        );

        Page<Hospital> hospitals = hospitalRepository.findAll(spec, pageable);

        // 엔티티 -> DTO 변환 (거리 계산 포함)
        return hospitals.map(hospital -> {
            Double distanceKm = null;
            // 위도/경도 정보가 DTO에 있는 경우 거리 계산 (추후 Entity에 좌표 저장 시 활용)
            if (condition.getLatitude() != null && condition.getLongitude() != null) {
                // 예시: 주소 기반 좌표 연동 시 거리 계산 적용 가능
            }
            return HospitalResponseDto.fromEntity(hospital, distanceKm);
        });
    }

    /**
     * 2. 의료기관 상세 정보 조회
     */
    public HospitalResponseDto getHospitalDetail(String hospitalId) {
        Hospital hospital = hospitalRepository.findByHospitalId(hospitalId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND, "해당 의료기관을 찾을 수 없습니다: " + hospitalId));

        return HospitalResponseDto.fromEntity(hospital, null);
    }
}