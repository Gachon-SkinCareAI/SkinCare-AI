package skincare.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import skincare.dto.*;
import skincare.service.InquiryService;
import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
@RestController @RequestMapping("/api/wc/admin/inquiries") @RequiredArgsConstructor
public class AdminInquiryController {
    private final InquiryService service;
    @GetMapping public ResponseEntity<ServicePage<InquiryView>> list(@RequestParam(required=false) InquiryStatus status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listAdmin(status,page,size));}
    @GetMapping("/{id}") public ResponseEntity<InquiryView> get(@PathVariable String id) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getAdmin(id));}
    @PutMapping("/{id}/answer") public ResponseEntity<InquiryView> answer(@AuthenticationPrincipal String admin,@PathVariable String id,@RequestBody InquiryReplyRequest body) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reply(admin,id,body));}
}
