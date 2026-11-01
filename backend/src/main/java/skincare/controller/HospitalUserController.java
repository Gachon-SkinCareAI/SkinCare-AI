package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.dto.ApiResponseDto;
import skincare.dto.FilterParam;
import skincare.dto.HospitalSearchConditionDto;
import skincare.service.HospitalQueryService;

// 사용자용 의료기관(병원/약국) 검색 및 상세 조회 API 요청 처리 컨트롤러
@Tag(name = "User - Hospital & Pharmacy", description = "사용자용 피부과/약국 검색 및 상세정보 API")
@RestController
@RequestMapping("/api/wc/hospitals")
@RequiredArgsConstructor
public class HospitalUserController {

    private final HospitalQueryService hospitalQueryService; // 의료기관 조회 비즈니스 로직 서비스

    // 내 주변 피부과/병원 및 약국 목록 조회 API
    @Operation(
            summary = "내 주변 의료기관 및 약국 목록 조회",
            description = "latitude/longitude(현재 위치, 입력 시 반경 내 거리순), radius_km(기본 3km), type(ALL, DERMATOLOGY, GENERAL_HOSPITAL, PHARMACY, OTHER), "
                    + "is_night_care, is_emergency, region, keyword, page(1부터), size 조건으로 검색합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponseDto> searchHospitals(
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "radius_km", required = false) Double radiusKm,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "is_night_care", required = false) Boolean isNightCare,
            @RequestParam(value = "is_emergency", required = false) Boolean isEmergency,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        HospitalSearchConditionDto condition = HospitalSearchConditionDto.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radiusKm(radiusKm)
                .hospitalType(FilterParam.toEnum(HospitalType.class, type))
                .isNightCare(isNightCare)
                .isEmergency(isEmergency)
                .region(region)
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();

        return ApiResponseDto.ok("주변 의료기관 목록 조회가 완료되었습니다.")
                .withPage("hospital_list", hospitalQueryService.searchHospitals(condition))
                .toResponseEntity();
    }

    // 의료기관 상세정보 조회 API
    @Operation(
            summary = "의료기관 상세정보 조회",
            description = "hospitalId(기관 고유 ID)로 특정 병원/약국의 진료시간, 연락처 등 상세 정보를 조회합니다."
    )
    @GetMapping("/{hospitalId}")
    public ResponseEntity<ApiResponseDto> getHospitalDetail(@PathVariable String hospitalId) {
        return ApiResponseDto.ok("의료기관 상세 정보 조회가 완료되었습니다.")
                .with("hospital_detail", hospitalQueryService.getHospitalDetail(hospitalId))
                .toResponseEntity();
    }
}
