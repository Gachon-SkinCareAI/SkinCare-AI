package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.domain.hospital.entity.Hospital;

import java.time.format.DateTimeFormatter;

// 의료기관(병원/약국) 목록 및 상세 조회 응답 DTO (GET /api/wc/hospitals, /api/wc/hospitals/{hospitalId})
// 목록 조회(hospital_list)는 기본 정보와 거리만, 상세 조회(hospital_detail)는 진료시간/출처/갱신일까지 포함
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
// HospitalResponseDto 요청과 응답 데이터
public class HospitalResponseDto {

    private static final String SYSTEM_NOTICE =
            "기관 정보는 공공데이터 기준으로 자동 관리됩니다. 신규 기관은 추가되고 폐업 기관은 다음 동기화 시 운영 중지 처리됩니다.";

    private String hospitalId; // 기관 고유 ID (ex. HIRA-xxxx)
    private String name; // 병원/약국 명칭
    private HospitalType type; // 기관 유형 코드 (DERMATOLOGY, GENERAL_HOSPITAL, PHARMACY, OTHER)
    private String typeLabel; // 기관 유형 표시명
    private String region; // 행정구역 지역명
    private String address; // 상세 주소
    private String tel; // 대표 전화번호
    private Double latitude; // 위도 (지도 표시용)
    private Double longitude; // 경도 (지도 표시용)

    @JsonProperty("is_night_care")
    private boolean nightCare; // 야간 진료 여부

    @JsonProperty("is_emergency")
    private boolean emergency; // 응급실 운영 여부

    private Double distanceKm; // 사용자 위치와의 거리 (km, 위치 검색 시에만 포함)
    private String status; // 운영 상태 (ACTIVE, INACTIVE)
    private OperatingHours operatingHours; // 진료시간 (상세 조회)
    private String dataSource; // 데이터 제공 출처 (상세 조회)
    private String lastUpdatedAt; // 정보 최종 갱신일 (상세 조회)
    private String description; // 안내 문구 (상세 조회)

    // 진료시간 정보
    @Getter
    @Builder
    public static class OperatingHours {
        private String weekday; // 평일 진료시간
        private String saturday; // 토요일 진료시간
        private String sundayHoliday; // 일요일/공휴일 진료시간
    }

    // 목록 조회용 응답 변환 (거리 정보 포함)
    public static HospitalResponseDto summary(Hospital hospital, Double distanceKm) {
        return baseBuilder(hospital)
                .distanceKm(
                        distanceKm != null
                                ? Math.round(distanceKm * 100.0) / 100.0
                                : null) // 소수점 둘째자리 반올림
                .build();
    }

    // 상세 조회용 응답 변환 (진료시간, 출처, 갱신일 포함)
    public static HospitalResponseDto detail(Hospital hospital) {
        return baseBuilder(hospital)
                .operatingHours(
                        OperatingHours.builder()
                                .weekday(hospital.getWeekdayHours())
                                .saturday(hospital.getSaturdayHours())
                                .sundayHoliday(hospital.getSundayHolidayHours())
                                .build())
                .dataSource(hospital.getDataSource())
                .lastUpdatedAt(
                        hospital.getUpdatedAt() != null
                                ? hospital.getUpdatedAt()
                                        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                                : null)
                .description(SYSTEM_NOTICE)
                .build();
    }

    // 병원 목록과 상세 필드 매핑
    private static HospitalResponseDtoBuilder baseBuilder(Hospital hospital) {
        return HospitalResponseDto.builder()
                .hospitalId(hospital.getHospitalId())
                .name(hospital.getName())
                .type(hospital.getHospitalType())
                .typeLabel(
                        hospital.getHospitalType() != null
                                ? hospital.getHospitalType().getLabel()
                                : null)
                .region(hospital.getRegion())
                .address(hospital.getAddress())
                .tel(hospital.getTel())
                .latitude(hospital.getLatitude())
                .longitude(hospital.getLongitude())
                .nightCare(hospital.isNightCare())
                .emergency(hospital.isEmergency())
                .status(hospital.getStatus());
    }
}
