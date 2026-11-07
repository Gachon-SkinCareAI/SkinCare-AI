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
import skincare.dto.ApiResponseDto;
import skincare.dto.FilterParam;
import skincare.dto.RecoveryGroupResponseDto;
import skincare.service.RecoveryService;

import java.util.List;

// 사용자 상처 회복 경과(Recovery) 기록 관련 API 요청 처리 컨트롤러
@Tag(name = "User - Recovery", description = "사용자 상처 회복 경과 기록 및 비교 조회 API")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="skincare.legacy.controllers-enabled",havingValue="true")
@RestController
@RequestMapping("/api/wc/recoveries")
@RequiredArgsConstructor
public class RecoveryController {

    private final RecoveryService recoveryService; // 회복 경과 기록 비즈니스 로직 서비스

    // 상처별 회복 경과 목록 및 비교 조회 API
    @Operation(
            summary = "부위별 상처 회복 기록 및 경과 비교 목록 조회",
            description = "body_part(ALL, ARM, LEG, FACE, TORSO, HAND_FOOT, OTHER), diagnosis_id(특정 상처만, 선택)로 필터링합니다. "
                    + "각 상처는 최초 분석 사진과 이후 회복 기록 타임라인으로 구성됩니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponseDto> getMyRecoveries(
            @AuthenticationPrincipal String userId,
            @RequestParam(value = "body_part", required = false) String bodyPart,
            @RequestParam(value = "diagnosis_id", required = false) String diagnosisId
    ) {
        List<RecoveryGroupResponseDto> groups = recoveryService.getMyRecoveries(
                userId, FilterParam.toEnum(BodyPart.class, bodyPart), diagnosisId);
        return ApiResponseDto.ok("회복 기록 목록 조회가 완료되었습니다.")
                .with("total_count", groups.size())
                .with("recovery_groups", groups)
                .toResponseEntity();
    }

    // 상처 회복 경과 사진 및 기록 등록 API
    @Operation(
            summary = "상처 회복 경과 사진 및 기록 등록",
            description = "multipart/form-data로 diagnosis_id(최초 분석 ID), image(경과 사진), pain_score(0~10, 선택), memo(선택)를 전송합니다. "
                    + "최초 분석 사진과의 환부 면적 변화 참고값(trend_hint)이 함께 저장됩니다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto> createRecovery(
            @AuthenticationPrincipal String userId,
            @RequestParam("diagnosis_id") String diagnosisId,
            @RequestPart("image") MultipartFile image,
            @RequestParam(value = "pain_score", required = false) Integer painScore,
            @RequestParam(value = "memo", required = false) String memo
    ) {
        return ApiResponseDto.created("상처 회복 경과 기록이 성공적으로 등록되었습니다.")
                .with("recovery_record", recoveryService.createRecovery(userId, diagnosisId, image, memo, painScore))
                .toResponseEntity();
    }

    // 회복 경과 사진 조회 API (이미지 바이너리 응답)
    @Operation(summary = "회복 경과 사진 조회", description = "본인 회복 기록의 경과 사진을 조회합니다.")
    @GetMapping("/{recoveryId}/image")
    public ResponseEntity<Resource> getRecoveryImage(
            @AuthenticationPrincipal String userId,
            @PathVariable String recoveryId
    ) {
        return recoveryService.getRecoveryImage(userId, recoveryId).toResponseEntity();
    }

    // 회복 경과 기록 삭제 API
    @Operation(summary = "회복 경과 기록 삭제", description = "본인 회복 기록과 경과 사진을 삭제합니다.")
    @DeleteMapping("/{recoveryId}")
    public ResponseEntity<ApiResponseDto> deleteRecovery(
            @AuthenticationPrincipal String userId,
            @PathVariable String recoveryId
    ) {
        recoveryService.deleteRecovery(userId, recoveryId);
        return ApiResponseDto.ok("회복 경과 기록이 삭제되었습니다.").toResponseEntity();
    }
}
