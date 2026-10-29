package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;

@Getter
@Builder
public class HospitalResponseDto {

    private String hospitalId;
    private String name;
    private HospitalType hospitalType;
    private String region;
    private String address;
    private String tel;
    private boolean isNightCare;
    private boolean isEmergency;
    private String dataSource;
    private String status;
    private Double distanceKm; // 사용자 위치와의 거리 (km)

    public static HospitalResponseDto fromEntity(Hospital hospital, Double distanceKm) {
        return HospitalResponseDto.builder()
                .hospitalId(hospital.getHospitalId())
                .name(hospital.getName())
                .hospitalType(hospital.getHospitalType())
                .region(hospital.getRegion())
                .address(hospital.getAddress())
                .tel(hospital.getTel())
                .isNightCare(hospital.isNightCare())
                .isEmergency(hospital.isEmergency())
                .dataSource(hospital.getDataSource())
                .status(hospital.getStatus())
                .distanceKm(distanceKm != null ? Math.round(distanceKm * 100.0) / 100.0 : null) // 소수점 둘째자리 반올림
                .build();
    }
}