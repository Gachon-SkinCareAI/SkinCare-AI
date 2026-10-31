package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import skincare.dto.DiagnosisRequestDto;
import skincare.dto.DiagnosisResponseDto;
import skincare.security.UserPrincipal;
import skincare.service.DiagnosisService;

@Tag(name = "User - AI Wound Diagnosis", description = "사용자 AI 상처 진단 및 이력 관리 API")
@RestController
@RequestMapping("/api/wc/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @Operation(
            summary = "상처 이미지 업로드 및 AI 분석 요청",
            description = "상처 사진 파일(image)과 신체 부위/증상 메모(data)를 전송하여 AI 상처 진단을 요청합니다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DiagnosisResponseDto> analyzeWound(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("image") MultipartFile imageFile,
            @Valid @RequestPart("data") DiagnosisRequestDto requestDto
    ) {
        DiagnosisResponseDto response = diagnosisService.analyzeWound(principal.getUserId(), imageFile, requestDto);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "본인 AI 진단 이력 목록 조회 (페이지네이션)",
            description = "사용자가 이전에 진행했던 전체 AI 상처 진단 이력을 최신순으로 조회합니다."
    )
    @GetMapping
    public ResponseEntity<Page<DiagnosisResponseDto>> getMyDiagnosisHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<DiagnosisResponseDto> response = diagnosisService.getMyDiagnosisHistory(principal.getUserId(), page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "AI 진단 결과 및 케어 가이드 상세 조회",
            description = "특정 diagnosisId의 의심 질환명, 위험도, 응급처치 가이드, AI 비전 메트릭 상세 정보를 조회합니다."
    )
    @GetMapping("/{diagnosisId}")
    public ResponseEntity<DiagnosisResponseDto> getDiagnosisDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String diagnosisId
    ) {
        DiagnosisResponseDto response = diagnosisService.getDiagnosisDetail(principal.getUserId(), diagnosisId);
        return ResponseEntity.ok(response);
    }
}