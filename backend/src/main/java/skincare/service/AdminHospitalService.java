package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;
import skincare.dto.HospitalResponseDto;
import skincare.dto.Paging;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.HospitalRepository;

import java.util.LinkedHashMap;
import java.util.Map;

// 관리자 의료기관/약국 관리 비즈니스 로직 처리
// 등록 기관 목록/통계 조회, 상세 조회, 운영 상태(노출/숨김) 관리 / 공공데이터 동기화는 HospitalSyncService에서 처리
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHospitalService {

    private final HospitalRepository hospitalRepository; // 의료기관 데이터 베이스 접근용 Repository

    // 등록 기관 목록 조회 (검색어, 기관 유형, 운영 상태 필터 / 운영 중지·숨김 기관 포함)
    public Page<HospitalResponseDto> getHospitals(String keyword, HospitalType type, String status, int page, int size) {
        return hospitalRepository.findAll(HospitalRepository.adminSearch(keyword, type, status),
                        Paging.of(page, size, Sort.by("name")))
                .map(hospital -> HospitalResponseDto.summary(hospital, null));
    }

    // 등록 기관 현황 통계 (운영 중 기관, 야간진료 병원, 야간 약국, 응급실, 운영 중지/숨김 기관 수)
    public Map<String, Object> getHospitalStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_registered", hospitalRepository.countByStatus(Hospital.STATUS_ACTIVE));
        stats.put("night_care_available", hospitalRepository.countByStatusAndNightCareTrueAndHospitalTypeNot(
                Hospital.STATUS_ACTIVE, HospitalType.PHARMACY));
        stats.put("night_pharmacies", hospitalRepository.countByStatusAndNightCareTrueAndHospitalType(
                Hospital.STATUS_ACTIVE, HospitalType.PHARMACY));
        stats.put("emergency_available", hospitalRepository.countByStatusAndEmergencyTrue(Hospital.STATUS_ACTIVE));
        stats.put("inactive_count", hospitalRepository.countByStatus(Hospital.STATUS_INACTIVE));
        stats.put("hidden_count", hospitalRepository.countByStatus(Hospital.STATUS_HIDDEN));
        return stats;
    }

    // 등록 기관 상세 조회 (숨김/운영 중지 기관 포함)
    public HospitalResponseDto getHospitalDetail(String hospitalId) {
        return HospitalResponseDto.detail(findHospital(hospitalId));
    }

    // 기관 운영 상태 변경 (ACTIVE: 노출, HIDDEN: 숨김)
    @Transactional
    public HospitalResponseDto changeStatus(String adminId, String hospitalId, String status) {
        Hospital hospital = findHospital(hospitalId);
        String previous = hospital.getStatus();
        hospital.changeStatusByAdmin(status);
        log.info("[Sync] 정상 처리, 의료기관 운영 상태 변경 (hospital_id: {}, {} → {}, admin_id: {})",
                hospitalId, previous, status, adminId);
        return HospitalResponseDto.detail(hospital);
    }

    // 기관 조회 (없으면 HOSPITAL-001)
    private Hospital findHospital(String hospitalId) {
        return hospitalRepository.findByHospitalId(hospitalId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
    }
}
