package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import skincare.external.AiStatusClient;
import skincare.service.OperationsService;

import java.util.Map;

@RestController
@RequestMapping("/api/wc/admin/operations")
@RequiredArgsConstructor
// AdminOperationsController API 요청 처리
public class AdminOperationsController {
    private final OperationsService operations;
    private final skincare.service.ServiceReadiness readiness;
    private final AiStatusClient ai;

    // 분석 결과 조회
    @GetMapping
    public ResponseEntity<Map<String, Object>> analyses(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operations.analyses(status, page, size));
    }

    @GetMapping("/readiness")
    public ResponseEntity<skincare.service.ServiceReadiness.Report> readiness() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(readiness.report());
    }

    // ai상태 조회
    @GetMapping("/ai-status")
    public ResponseEntity<Map<String, Object>> aiStatus() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ai.status());
    }
}
