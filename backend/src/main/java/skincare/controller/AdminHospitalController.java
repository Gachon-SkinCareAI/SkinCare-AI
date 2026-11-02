package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.dto.AdminHospitalStatusUpdateRequestDto;
import skincare.dto.ApiResponseDto;
import skincare.dto.FilterParam;
import skincare.service.AdminHospitalService;

// 관리자 의료기관/약국 관리 API 요청 처리 컨트롤러 (공공데이터 동기화는 AdminController의 /hospitals/sync)
@Tag(name = "Admin - Hospital Management", description = "관리자 전용 등록 의료기관/약국 조회 및 운영 상태 관리 API")
@RestController
@RequestMapping("/api/wc/admin/hospitals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHospitalController {

    private final AdminHospitalService adminHospitalService; // 관리자 의료기관 관리 비즈니스 로직 서비스

    // 등록 의료기관/약국 목록 조회 API
    @Operation(
            summary = "등록 의료기관/약국 목록 조회 및 검색",
            description = "search_keyword(기관명/주소/ID), type(ALL, DERMATOLOGY, GENERAL_HOSPITAL, PHARMACY, OTHER), "
                    + "status(ALL, ACTIVE, INACTIVE, HIDDEN), page(1부터), size"
    )
    @GetMapping
    public ResponseEntity<ApiResponseDto> getHospitals(
            @RequestParam(value = "search_keyword", required = false) String searchKeyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponseDto.ok("등록 의료기관 목록 조회가 완료되었습니다.")
                .with("stats", adminHospitalService.getHospitalStats())
                .withPage("hospital_list", adminHospitalService.getHospitals(searchKeyword,
                        FilterParam.toEnum(HospitalType.class, type), status, page, size))
                .toResponseEntity();
    }

    // 의료기관 상세 정보 조회 API
    @Operation(summary = "의료기관 상세 정보 조회", description = "운영 중지/숨김 기관을 포함하여 기관 상세 정보를 조회합니다.")
    @GetMapping("/{hospitalId}")
    public ResponseEntity<ApiResponseDto> getHospitalDetail(@PathVariable String hospitalId) {
        return ApiResponseDto.ok("의료기관 상세 정보 조회가 완료되었습니다.")
                .with("hospital_detail", adminHospitalService.getHospitalDetail(hospitalId))
                .toResponseEntity();
    }

    // 의료기관 운영 상태 변경 API
    @Operation(summary = "의료기관 운영 상태 변경", description = "status(ACTIVE: 노출, HIDDEN: 숨김)를 변경합니다. 숨김 기관은 공공데이터 동기화 후에도 숨김이 유지됩니다.")
    @PatchMapping("/{hospitalId}/status")
    public ResponseEntity<ApiResponseDto> changeStatus(
            @AuthenticationPrincipal String adminId,
            @PathVariable String hospitalId,
            @Valid @RequestBody AdminHospitalStatusUpdateRequestDto requestDto
    ) {
        return ApiResponseDto.ok("의료기관 운영 상태가 변경되었습니다.")
                .with("hospital_detail", adminHospitalService.changeStatus(adminId, hospitalId, requestDto.getStatus()))
                .toResponseEntity();
    }
}
