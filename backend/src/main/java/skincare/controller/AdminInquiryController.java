package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.dto.*;
import skincare.service.InquiryService;

@RestController
@RequestMapping("/api/wc/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final InquiryService service;

    // 문의 리스트 조회
    @GetMapping
    public ResponseEntity<ServicePage<InquiryView>> list(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listAdmin(status, page, size));
    }

    // 문의 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<InquiryView> get(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getAdmin(id));
    }

    // 문의 답변 등록
    @PutMapping("/{id}/answer")
    public ResponseEntity<InquiryView> answer(
            @AuthenticationPrincipal String admin,
            @PathVariable String id,
            @RequestBody InquiryReplyRequest body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.reply(admin, id, body));
    }
}
