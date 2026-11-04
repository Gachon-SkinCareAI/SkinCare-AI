package skincare.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import skincare.service.*;
@RestController @RequestMapping("/api/wc/admin") @RequiredArgsConstructor
public class AdminReviewController {
    private final AdminAuditService audit;private final ContentReviewService reviews;
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping("/audit") public ResponseEntity<?> audit(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ok(audit.list(page,size));}
    @GetMapping("/reviews/capabilities") public ResponseEntity<?> capabilities(@AuthenticationPrincipal String actor){return ok(reviews.capabilities(actor));}
    @GetMapping("/reviews/catalog/{kind}") public ResponseEntity<?> catalog(@PathVariable String kind){return ok(reviews.catalog(kind));}
    @GetMapping("/reviews") public ResponseEntity<?> list(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ok(reviews.list(status,page,size));}
    @GetMapping("/reviews/{id}") public ResponseEntity<?> get(@PathVariable String id){return ok(reviews.get(id));}
    @PostMapping("/reviews") public ResponseEntity<?> create(@AuthenticationPrincipal String actor,@RequestBody ContentReviewService.Draft body){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(reviews.create(actor,body));}
    @PutMapping("/reviews/{id}/state") public ResponseEntity<?> transition(@AuthenticationPrincipal String actor,@PathVariable String id,@RequestBody ContentReviewService.Transition body){return ok(reviews.transition(actor,id,body));}
}
