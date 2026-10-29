package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import skincare.dto.HospitalResponseDto;
import skincare.dto.HospitalSearchConditionDto;
import skincare.service.HospitalQueryService;

@Tag(name = "User - Hospital & Pharmacy", description = "사용자용 피부과/약국 검색 및 상세정보 API")
@RestController
@RequestMapping("/api/wc/hospitals")
@RequiredArgsConstructor
public class HospitalUserController {

    private final HospitalQueryService hospitalQueryService;

    @Operation(
            summary = "피부과 및 약국 조건별 목록 검색",
            description = "지역, 키워드, 카테고리(피부과/약국/종합병원), 야간진료 여부, 응급실 여부 등의 조건으로 목록을 검색합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<Page<HospitalResponseDto>> searchHospitals(
            @ModelAttribute HospitalSearchConditionDto condition
    ) {
        Page<HospitalResponseDto> result = hospitalQueryService.searchHospitals(condition);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "의료기관 상세정보 조회",
            description = "hospitalId(기관 고유 ID)로 특정 병원/약국의 상세 정보를 조회합니다."
    )
    @GetMapping("/{hospitalId}")
    public ResponseEntity<HospitalResponseDto> getHospitalDetail(
            @PathVariable String hospitalId
    ) {
        HospitalResponseDto response = hospitalQueryService.getHospitalDetail(hospitalId);
        return ResponseEntity.ok(response);
    }
}