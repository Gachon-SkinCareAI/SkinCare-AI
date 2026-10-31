package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import skincare.domain.diagnosis.entity.ENUM.DiagnosisStatus;
import skincare.dto.AdminDiagnosisResponseDto;
import skincare.dto.AdminDiagnosisStatusUpdateRequestDto;
import skincare.service.AdminDiagnosisService;

@Tag(name = "Admin - Diagnosis Management", description = "관리자 전용 사용자 AI 진단건 관리 API")
@RestController
@RequestMapping("/api/wc/admin/diagnoses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiagnosisController {

    private final AdminDiagnosisService adminDiagnosisService;

    @Operation(
            summary = "전체 사용자 진단건 목록 조회 (페이지네이션)",
            description = "전체 사용자의 AI 상처 진단 내역을 최신순으로 조회합니다. status 파라미터(PENDING, IN_PROGRESS, COMPLETED, ALERT_SENT)로 상태별 필터링이 가능합니다."
    )
    @GetMapping
    public ResponseEntity<Page<AdminDiagnosisResponseDto>> getAllDiagnoses(
            @RequestParam(required = false) DiagnosisStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<AdminDiagnosisResponseDto> response = adminDiagnosisService.getAllDiagnoses(status, page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "진단건 상세내용 조회",
            description = "diagnosisId(ex. DX-20260929-0438)를 통해 사용자 정보, 촬영 이미지, AI 예측 질환 및 세부 비전 메트릭을 상세 조회합니다."
    )
    @GetMapping("/{diagnosisId}")
    public ResponseEntity<AdminDiagnosisResponseDto> getDiagnosisDetail(
            @PathVariable String diagnosisId
    ) {
        AdminDiagnosisResponseDto response = adminDiagnosisService.getDiagnosisDetail(diagnosisId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "진단건 처리 상태 및 메모 변경",
            description = "특정 진단건의 처리 상태(PENDING, IN_PROGRESS, COMPLETED, ALERT_SENT) 및 관리자 확인 메모(adminNote)를 업데이트합니다."
    )
    @PatchMapping("/{diagnosisId}/status")
    public ResponseEntity<AdminDiagnosisResponseDto> updateDiagnosisStatus(
            @PathVariable String diagnosisId,
            @Valid @RequestBody AdminDiagnosisStatusUpdateRequestDto requestDto
    ) {
        AdminDiagnosisResponseDto response = adminDiagnosisService.updateDiagnosisStatus(diagnosisId, requestDto);
        return ResponseEntity.ok(response);
    }
}