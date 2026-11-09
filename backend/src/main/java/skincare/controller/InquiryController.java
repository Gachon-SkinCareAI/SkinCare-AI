package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import skincare.dto.InquiryRequest;
import skincare.dto.InquiryView;
import skincare.dto.ServicePage;
import skincare.service.InquiryService;

@RestController
@RequestMapping("/api/wc/inquiries")
@RequiredArgsConstructor
// InquiryController API 요청 처리
public class InquiryController {
    private final InquiryService service;

    @PostMapping
    public ResponseEntity<InquiryView> create(
            @AuthenticationPrincipal String owner, @RequestBody InquiryRequest body) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(service.create(owner, body));
    }

    @GetMapping
    public ResponseEntity<ServicePage<InquiryView>> list(
            @AuthenticationPrincipal String owner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(owner, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InquiryView> get(
            @AuthenticationPrincipal String owner, @PathVariable String id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.get(owner, id));
    }
}
