package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import skincare.dto.AiRetrainRequestDto;
import skincare.dto.ApiResponseDto;
import skincare.service.AdminAiMonitoringService;

// 관리자 AI 모델 성능, 파이프라인 상태, MLOps 재학습 이력 모니터링 API 요청 처리 컨트롤러
@Tag(name = "Admin - AI Monitoring", description = "관리자 전용 AI 모델 성능, 파이프라인, MLOps 재학습 이력 모니터링 API")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="skincare.legacy.controllers-enabled",havingValue="true")
@RestController
@RequestMapping("/api/wc/admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiMonitoringController {

    private final AdminAiMonitoringService adminAiMonitoringService; // AI 모니터링 비즈니스 로직 서비스

    // AI 모델 성능 지표 목록 조회 API
    @Operation(
            summary = "AI 모델 Performance 지표 요청",
            description = "대시보드 노출용 AI 비전 모델의 Accuracy, Recall, IoU, Precision 등 성능 평가 지표를 조회합니다."
    )
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponseDto> getAiModelMetrics() {
        return ApiResponseDto.ok("AI 모델 Performance 지표 조회가 완료되었습니다.")
                .with("metrics_summary", adminAiMonitoringService.getAiModelMetrics())
                .toResponseEntity();
    }

    // AI 파이프라인 엔진별 상태 및 응답속도 조회 API
    @Operation(
            summary = "AI 파이프라인 엔진별 상태 및 응답속도 정보 요청",
            description = "비전 추론 파이프라인의 가동 상태(HEALTHY/UNHEALTHY), 평균 응답속도(avg_latency), 리소스 점유율을 모니터링합니다."
    )
    @GetMapping("/pipelines")
    public ResponseEntity<ApiResponseDto> getAiPipelines() {
        return ApiResponseDto.ok("AI 파이프라인 상태 정보 조회가 완료되었습니다.")
                .with("pipeline_status", adminAiMonitoringService.getAiPipelines())
                .toResponseEntity();
    }

    // MLOps 모델 재학습 이력 조회 API (페이지네이션)
    @Operation(
            summary = "MLOps 모델 재학습 이력 정보 요청",
            description = "MLOps 비전 모델의 재학습 수행 일시, 트리거 사유, 학습 후 성능 변화, 결과 상태 이력을 페이지네이션 형태로 조회합니다."
    )
    @GetMapping("/retrain-history")
    public ResponseEntity<ApiResponseDto> getAiRetrainHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return ApiResponseDto.ok("MLOps 모델 재학습 이력 조회가 완료되었습니다.")
                .withPage("retrain_history", adminAiMonitoringService.getAiRetrainHistory(page, limit))
                .toResponseEntity();
    }

    // AI 서버 상태 조회 API
    @Operation(summary = "AI 서버 상태 조회", description = "AI 서버의 모델 적재 상태(ok/degraded/starting/unreachable), 모델·문항 뱅크·케어 카드 버전, 진행 중 추론 수를 조회합니다.")
    @GetMapping("/health")
    public ResponseEntity<ApiResponseDto> getAiHealth() {
        return ApiResponseDto.ok("AI 서버 상태 조회가 완료되었습니다.")
                .with("ai_health", adminAiMonitoringService.getAiHealth())
                .toResponseEntity();
    }

    // 재학습 후보 현황 조회 API
    @Operation(summary = "재학습 후보 현황 조회", description = "학습 활용에 동의(training_use=true)한 분석 건수와 의심 유형별 분포를 조회합니다.")
    @GetMapping("/retrain-candidates")
    public ResponseEntity<ApiResponseDto> getRetrainCandidates() {
        return ApiResponseDto.ok("재학습 후보 현황 조회가 완료되었습니다.")
                .with("retrain_candidates", adminAiMonitoringService.getRetrainCandidates())
                .toResponseEntity();
    }

    // 재학습 요청 API
    @Operation(summary = "MLOps 재학습 요청", description = "reason(manual, recall_drop, feedback_threshold)으로 AI 서버에 재학습 잡을 등록합니다. 등록 직후 상태는 queued입니다.")
    @PostMapping("/retrain")
    public ResponseEntity<ApiResponseDto> requestRetrain(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody AiRetrainRequestDto requestDto
    ) {
        return ApiResponseDto.created("재학습 잡이 등록되었습니다.")
                .with("retrain_job", adminAiMonitoringService.requestRetrain(adminId, requestDto.getReason()))
                .toResponseEntity();
    }

    // 재학습 잡 상태 조회 API
    @Operation(summary = "재학습 잡 상태 조회", description = "AI 서버의 잡 상태(queued → running → validating → awaiting_deploy → deployed / failed)를 조회하여 이력에 반영합니다.")
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponseDto> getJob(@PathVariable String jobId) {
        return ApiResponseDto.ok("재학습 잡 상태 조회가 완료되었습니다.")
                .with("retrain_job", adminAiMonitoringService.getJob(jobId))
                .toResponseEntity();
    }

    // 신규 모델 배포 승인 API
    @Operation(summary = "신규 모델 배포 승인", description = "검증을 통과하여 awaiting_deploy 상태인 재학습 모델의 배포를 승인합니다. (관리자 승인 없이 운영 모델은 변경되지 않음)")
    @PostMapping("/jobs/{jobId}/approve")
    public ResponseEntity<ApiResponseDto> approveDeploy(
            @AuthenticationPrincipal String adminId,
            @PathVariable String jobId
    ) {
        return ApiResponseDto.ok("신규 모델 배포가 승인되었습니다.")
                .with("retrain_job", adminAiMonitoringService.approveDeploy(adminId, jobId))
                .toResponseEntity();
    }
}