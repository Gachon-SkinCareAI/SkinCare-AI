package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import skincare.dto.ApiResponseDto;
import skincare.service.AdminAiMonitoringService;

// 관리자 AI 모델 성능, 파이프라인 상태, MLOps 재학습 이력 모니터링 API 요청 처리 컨트롤러
@Tag(name = "Admin - AI Monitoring", description = "관리자 전용 AI 모델 성능, 파이프라인, MLOps 재학습 이력 모니터링 API")
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
}