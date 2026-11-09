package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import skincare.domain.inquiry.entity.ENUM.InquiryStatus;
import skincare.repository.*;

import java.util.Map;

@RestController
@RequestMapping("/api/wc/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final InquiryRepository inquiries;
    private final HospitalRepository hospitals;

    // 대시보드 조회
    @GetMapping
    public ResponseEntity<Map<String, Long>> dashboard() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        Map.of(
                                "waitingInquiries", inquiries.countByStatus(InquiryStatus.WAITING),
                                "completedInquiries",
                                        inquiries.countByStatus(InquiryStatus.COMPLETED),
                                "activeHospitals", hospitals.countByStatus("ACTIVE"),
                                "inactiveHospitals", hospitals.countByStatus("INACTIVE")));
    }
}
