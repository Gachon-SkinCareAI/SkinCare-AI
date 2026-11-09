package skincare.dto;

import skincare.domain.hospital.entity.ENUM.HospitalType;

import java.time.LocalDate;

// HospitalRequest 요청과 응답 데이터
public record HospitalRequest(
        String name,
        HospitalType type,
        String region,
        String address,
        String tel,
        Boolean nightCare,
        Boolean emergency,
        String dataSource,
        String sourceUrl,
        LocalDate verifiedOn,
        String status,
        Long expectedVersion,
        String clientRequestId) {}
