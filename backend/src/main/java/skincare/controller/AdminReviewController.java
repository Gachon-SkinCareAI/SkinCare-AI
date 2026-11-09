package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import skincare.service.*;

@RestController
@RequestMapping("/api/wc/admin")
@RequiredArgsConstructor
// AdminReviewController API 요청 처리
public class AdminReviewController {

    private final AdminAuditService auditService;
    private final ContentReviewService reviewService;

    private ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    // 관리자 리스트 조회
    @GetMapping("/audit")
    public ResponseEntity<?> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(auditService.list(page, size));
    }

    // 리뷰 기능 조회
    @GetMapping("/reviews/capabilities")
    public ResponseEntity<?> capabilities(@AuthenticationPrincipal String actor) {
        return ok(reviewService.capabilities(actor));
    }

    // 리뷰 카탈로그 조회
    @GetMapping("/reviews/catalog/{kind}")
    public ResponseEntity<?> catalog(@PathVariable String kind) {
        return ok(reviewService.catalog(kind));
    }

    // 리뷰 리스트 조회
    @GetMapping("/reviews")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(reviewService.list(status, page, size));
    }

    // 리뷰 상세 조회
    @GetMapping("/reviews/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return ok(reviewService.get(id));
    }

    // 리뷰 생성
    @PostMapping("/reviews")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal String actor, @RequestBody ContentReviewService.Draft body) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(reviewService.create(actor, body));
    }

    // 리뷰 상태 전환
    @PutMapping("/reviews/{id}/state")
    public ResponseEntity<?> transition(
            @AuthenticationPrincipal String actor,
            @PathVariable String id,
            @RequestBody ContentReviewService.Transition body) {
        return ok(reviewService.transition(actor, id, body));
    }
}
