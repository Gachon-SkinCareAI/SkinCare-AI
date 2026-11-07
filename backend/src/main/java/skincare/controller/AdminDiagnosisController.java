package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.dto.AdminDiagnosisStatusUpdateRequestDto;
import skincare.dto.ApiResponseDto;
import skincare.dto.FilterParam;
import skincare.service.AdminDiagnosisService;

// 관리자 AI 분석 로그 관리 API 요청 처리 컨트롤러
@Tag(name = "Admin - Analysis Log Management", description = "관리자 전용 사용자 AI 분석 로그 관리 API")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="skincare.legacy.controllers-enabled",havingValue="true")
@RestController
@RequestMapping("/api/wc/admin/diagnoses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiagnosisController {

    private final AdminDiagnosisService adminDiagnosisService; // 관리자 분석 로그 관리 비즈니스 로직 서비스

    // 전체 사용자 분석 로그 목록 조회 API (페이지네이션, 위험도/상태 필터, 검색)
    @Operation(
            summary = "분석 로그 전체 목록 조회 (페이지네이션)",
            description = "risk_level(ALL, OBSERVATION, WARNING, EMERGENCY, UNKNOWN), status(ALL, PENDING, IN_PROGRESS, COMPLETED, ALERT_SENT), "
                    + "search_keyword(분석 ID/사용자 ID/이메일/닉네임), page(1부터), size"
    )
    @GetMapping
    public ResponseEntity<ApiResponseDto> getAllDiagnoses(
            @RequestParam(value = "risk_level", required = false) String riskLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search_keyword", required = false) String searchKeyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponseDto.ok("분석 로그 목록 조회가 완료되었습니다.")
                .withPage("diagnoses_list", adminDiagnosisService.getAllDiagnoses(
                        FilterParam.toEnum(RiskLevel.class, riskLevel),
                        FilterParam.toEnum(DiagnosisStatus.class, status),
                        searchKeyword, page, size))
                .toResponseEntity();
    }

    // 분석 로그 상세내용 조회 API
    @Operation(
            summary = "분석 로그 상세 내역 조회",
            description = "사용자 정보, 분석 이미지, AI 의심 유형/위험도, 문진 내용, 적용 케어 가이드, AI 모델 버전, 처리 이력을 상세 조회합니다."
    )
    @GetMapping("/{diagnosisId}")
    public ResponseEntity<ApiResponseDto> getDiagnosisDetail(@PathVariable String diagnosisId) {
        return ApiResponseDto.ok("특정 분석 로그 상세 내역 조회가 완료되었습니다.")
                .with("diagnosis_detail", adminDiagnosisService.getDiagnosisDetail(diagnosisId))
                .toResponseEntity();
    }

    // 분석 이미지 조회 API (이미지 바이너리 응답)
    @Operation(summary = "분석 이미지 조회", description = "분석 로그의 상처 사진을 조회합니다.")
    @GetMapping("/{diagnosisId}/image")
    public ResponseEntity<Resource> getDiagnosisImage(@PathVariable String diagnosisId) {
        return adminDiagnosisService.getDiagnosisImage(diagnosisId).toResponseEntity();
    }

    // 분석건 처리 상태/검수 정보 변경 API
    @Operation(
            summary = "분석건 처리 상태 및 검수 정보 변경",
            description = "status(PENDING, IN_PROGRESS, COMPLETED, ALERT_SENT), review_status(NORMAL, NEEDS_REVIEW, MISCLASSIFICATION_SUSPECTED), "
                    + "admin_note를 변경합니다. 변경 관리자와 처리 시각은 처리 이력으로 저장됩니다."
    )
    @PatchMapping("/{diagnosisId}/status")
    public ResponseEntity<ApiResponseDto> updateDiagnosisStatus(
            @AuthenticationPrincipal String adminId,
            @PathVariable String diagnosisId,
            @Valid @RequestBody AdminDiagnosisStatusUpdateRequestDto requestDto
    ) {
        return ApiResponseDto.ok("분석건 처리 상태가 성공적으로 변경되었습니다.")
                .with("updated_status_info", adminDiagnosisService.updateDiagnosisStatus(diagnosisId, requestDto, adminId))
                .toResponseEntity();
    }
}
