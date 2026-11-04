package skincare.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import skincare.dto.*;
import skincare.service.InquiryService;
@RestController @RequestMapping("/api/wc/inquiries") @RequiredArgsConstructor
public class InquiryController {
    private final InquiryService service;
    @PostMapping public ResponseEntity<InquiryView> create(@AuthenticationPrincipal String owner,@RequestBody InquiryRequest body) { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(owner,body)); }
    @GetMapping public ResponseEntity<ServicePage<InquiryView>> list(@AuthenticationPrincipal String owner,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(owner,page,size)); }
    @GetMapping("/{id}") public ResponseEntity<InquiryView> get(@AuthenticationPrincipal String owner,@PathVariable String id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(owner,id)); }
}
