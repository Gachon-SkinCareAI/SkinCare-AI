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
import jakarta.validation.Valid;
import skincare.dto.*;
import skincare.service.DiagnosisService;
import skincare.service.QuestionnaireService;

import java.util.Map;

// 사용자 AI 피부·상처 분석(Diagnosis) 관련 API 요청 처리 컨트롤러
@Tag(name = "User - AI Wound Analysis", description = "사용자 AI 피부·상처 분석 및 이력 관리 API")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="skincare.legacy.controllers-enabled",havingValue="true")
@RestController
@RequestMapping("/api/wc/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService; // AI 상처 분석 비즈니스 로직 서비스
    private final QuestionnaireService questionnaireService; // 동적 문진 진행 서비스

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

    // 동적 문진 진행 API
    @Operation(
            summary = "동적 문진 응답 제출 및 다음 질문 조회",
            description = "첫 질문은 본문 없이 요청하고, 이후 next_question.question_id와 answer를 전송합니다. "
                    + "문진이 끝나면(done=true) 위험도와 케어 가이드가 재평가된 diagnosis_result를 반환합니다. (최대 10턴)"
    )
    @PostMapping("/{diagnosisId}/questionnaire")
    public ResponseEntity<ApiResponseDto> answerQuestionnaire(
            @AuthenticationPrincipal String userId,
            @PathVariable String diagnosisId,
            @RequestBody(required = false) QuestionnaireAnswerRequestDto requestDto
    ) {
        Map<String, Object> result = questionnaireService.answer(userId, diagnosisId,
                requestDto != null ? requestDto : new QuestionnaireAnswerRequestDto());
        ApiResponseDto response = ApiResponseDto.ok(Boolean.TRUE.equals(result.get("done"))
                ? "문진이 완료되어 위험도가 재평가되었습니다." : "다음 문진 질문 조회가 완료되었습니다.");
        result.forEach(response::with);
        return response.toResponseEntity();
    }

    // AI 분석 결과 피드백 전송 API
    @Operation(
            summary = "AI 분석 결과 피드백 전송",
            description = "kind(AGREE, DISAGREE, OTHER)와 note(선택)를 전송합니다. 학습 활용에 동의한 분석만 재학습 후보로 사용됩니다."
    )
    @PostMapping("/{diagnosisId}/feedback")
    public ResponseEntity<ApiResponseDto> sendFeedback(
            @AuthenticationPrincipal String userId,
            @PathVariable String diagnosisId,
            @Valid @RequestBody DiagnosisFeedbackRequestDto requestDto
    ) {
        return ApiResponseDto.ok("분석 결과 피드백이 접수되었습니다.")
                .with("feedback_info", diagnosisService.sendFeedback(userId, diagnosisId, requestDto.getKind(), requestDto.getNote()))
                .toResponseEntity();
    }

    // 분석 사진 학습 활용 동의 변경 API
    @Operation(summary = "학습 활용 동의 변경", description = "training_use(true: 동의, false: 철회)로 분석 사진의 재학습 후보 활용 여부를 변경합니다.")
    @PatchMapping("/{diagnosisId}/training-use")
    public ResponseEntity<ApiResponseDto> updateTrainingUse(
            @AuthenticationPrincipal String userId,
            @PathVariable String diagnosisId,
            @Valid @RequestBody TrainingUseRequestDto requestDto
    ) {
        return ApiResponseDto.ok("학습 활용 동의 여부가 변경되었습니다.")
                .with("training_use_info", diagnosisService.updateTrainingUse(userId, diagnosisId, requestDto.getTrainingUse()))
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
