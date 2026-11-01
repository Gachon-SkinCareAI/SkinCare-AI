package skincare.dto;

import lombok.Builder;
import lombok.Getter;
import skincare.domain.hospital.entity.ENUM.HospitalType;

// 의료기관(병원/약국) 조건별 검색 조건 DTO (GET /api/wc/hospitals 쿼리 파라미터를 컨트롤러에서 변환)
@Getter
@Builder
public class HospitalSearchConditionDto {

    // 위치 기반 검색 (사용자 현재 위치, 위도/경도가 모두 있으면 반경 검색 + 거리순 정렬)
    private Double latitude;     // 위도
    private Double longitude;    // 경도
    private Double radiusKm;     // 검색 반경 (km, 미입력 시 3.0km)

    // 행정구역 검색 (ex. "서울 강남구")
    private String region;

    // 병원/약국 이름 또는 주소 검색어
    private String keyword;

    // 카테고리 필터 (DERMATOLOGY: 피부과, PHARMACY: 약국, GENERAL_HOSPITAL: 종합병원, OTHER: 병원/의원)
    private HospitalType hospitalType;

    // 조건 필터
    private Boolean isNightCare;   // 야간진료 여부 (true인 것만)
    private Boolean isEmergency;   // 응급실 여부 (true인 것만)

    // 페이징 (1부터 시작)
    private int page;  // 요청 페이지 번호
    private int size;  // 페이지당 조회 건수
}
