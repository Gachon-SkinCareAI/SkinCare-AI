package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.domain.diagnosis.entity.ENUM.RiskLevel;
import skincare.dto.ApiResponseDto;
import skincare.dto.DiagnosisResponseDto;
import skincare.dto.FilterParam;
import skincare.service.DiagnosisService;

// 사용자 AI 피부·상처 분석(Diagnosis) 관련 API 요청 처리 컨트롤러
@Tag(name = "User - AI Wound Analysis", description = "사용자 AI 피부·상처 분석 및 이력 관리 API")
@RestController
@RequestMapping("/api/wc/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService; // AI 상처 분석 비즈니스 로직 서비스

    // 상처 이미지 업로드 및 AI 분석 요청 API
    @Operation(
            summary = "상처 이미지 업로드 및 AI 분석 요청",
            description = "multipart/form-data로 image(사진 파일), body_part(ARM, LEG, FACE, TORSO, HAND_FOOT, OTHER), "
                    + "symptoms(증상 메모, 선택), training_use(학습 활용 동의, 선택)를 전송하여 AI 분석을 요청합니다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto> analyzeWound(
            @AuthenticationPrincipal String userId,
            @RequestPart("image") MultipartFile imageFile,
            @RequestParam("body_part") BodyPart bodyPart,
            @RequestParam(value = "symptoms", required = false) String symptoms,
            @RequestParam(value = "training_use", defaultValue = "false") boolean trainingUse
    ) {
        DiagnosisResponseDto response = diagnosisService.analyzeWound(userId, imageFile, bodyPart, symptoms, trainingUse);
        return ApiResponseDto.created("AI 상처 분석이 성공적으로 완료되었습니다.")
                .with("diagnosis_result", response)
                .toResponseEntity();
    }

    // 본인 AI 분석 이력 목록 조회 API (페이지네이션)
    @Operation(
            summary = "본인 AI 분석 이력 목록 조회 (페이지네이션)",
            description = "사용자가 이전에 진행했던 AI 분석 이력을 최신순으로 조회합니다. "
                    + "risk_level(ALL, OBSERVATION, WARNING, EMERGENCY, UNKNOWN), page(1부터), size"
    )
    @GetMapping
    public ResponseEntity<ApiResponseDto> getMyDiagnosisHistory(
            @AuthenticationPrincipal String userId,
            @RequestParam(value = "risk_level", required = false) String riskLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponseDto.ok("피부 분석 히스토리 목록 조회가 완료되었습니다.")
                .withPage("diagnoses_list", diagnosisService.getMyDiagnosisHistory(
                        userId, FilterParam.toEnum(RiskLevel.class, riskLevel), page, size))
                .toResponseEntity();
    }

    // AI 분석 결과 및 케어 가이드 상세 조회 API
    @Operation(
            summary = "AI 분석 결과 및 케어 가이드 상세 조회",
            description = "특정 diagnosisId의 의심 유형, 위험도, 공인 케어 가이드를 조회합니다."
    )
    @GetMapping("/{diagnosisId}")
    public ResponseEntity<ApiResponseDto> getDiagnosisDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable String diagnosisId
    ) {
        return ApiResponseDto.ok("분석 상세 정보 조회가 완료되었습니다.")
                .with("diagnosis_detail", diagnosisService.getDiagnosisDetail(userId, diagnosisId))
                .toResponseEntity();
    }

    // 본인 분석 이미지 조회 API (이미지 바이너리 응답)
    @Operation(summary = "분석 이미지 조회", description = "본인 분석 기록의 상처 사진을 조회합니다.")
    @GetMapping("/{diagnosisId}/image")
    public ResponseEntity<Resource> getDiagnosisImage(
            @AuthenticationPrincipal String userId,
            @PathVariable String diagnosisId
    ) {
        return diagnosisService.getDiagnosisImage(userId, diagnosisId).toResponseEntity();
    }
}
