package skincare.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import skincare.service.OperationsService;
import skincare.external.AiStatusClient;
import java.util.Map;
@RestController @RequestMapping("/api/wc/admin/operations") @RequiredArgsConstructor
public class AdminOperationsController {
    private final OperationsService operations;
    private final skincare.service.ServiceReadiness readiness;
    private final AiStatusClient ai;
    @GetMapping public ResponseEntity<Map<String, Object>> analyses(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operations.analyses(status, page, size));
    }
    @GetMapping("/readiness") public ResponseEntity<skincare.service.ServiceReadiness.Report> readiness() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(readiness.report());
    }
    @GetMapping("/ai-status") public ResponseEntity<Map<String, Object>> aiStatus() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ai.status());
    }
}
