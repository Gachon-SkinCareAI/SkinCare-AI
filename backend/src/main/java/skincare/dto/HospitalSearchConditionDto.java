package skincare.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import skincare.domain.hospital.entity.ENUM.HospitalType;

@Getter
@Setter
@NoArgsConstructor
public class HospitalSearchConditionDto {

    // 위치 기반 검색 (사용자 현재 위치)
    private Double latitude;     // 위도
    private Double longitude;    // 경도
    private Double radiusKm;     // 검색 반경 (km 기준, 기본값: 3.0km)

    // 행정구역 검색 (ex. "서울특별시 강남구" 또는 "경기도 성남시")
    private String region;

    // 병원/약국 이름 검색어
    private String keyword;

    // 카테고리 필터 (DERMATOLOGY: 피부과, PHARMACY: 약국, GENERAL_HOSPITAL: 종합병원 등)
    private HospitalType hospitalType;

    // 조건 필터
    private Boolean isNightCare;   // 야간진료 여부 (true인 것만)
    private Boolean isEmergency;   // 응급실 여부 (true인 것만)

    // 페이징 (0부터 시작)
    private Integer page = 0;
    private Integer size = 20;
}