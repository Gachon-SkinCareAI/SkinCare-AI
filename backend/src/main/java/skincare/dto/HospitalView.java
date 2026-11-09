package skincare.dto;

import skincare.domain.hospital.entity.Hospital;

import java.time.LocalDate;

// HospitalView 요청과 응답 데이터
public record HospitalView(
        String id,
        String name,
        String type,
        String region,
        String address,
        String tel,
        boolean nightCare,
        boolean emergency,
        String dataSource,
        String sourceUrl,
        LocalDate verifiedOn,
        String status,
        long version) {
    public static HospitalView from(Hospital h) {
        return new HospitalView(
                h.getHospitalId(),
                h.getName(),
                h.getHospitalType().name(),
                h.getRegion(),
                h.getAddress(),
                h.getTel(),
                h.isNightCare(),
                h.isEmergency(),
                h.getDataSource(),
                h.getSourceUrl(),
                h.getVerifiedOn(),
                h.getStatus(),
                h.getVersion());
    }
}
