package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import skincare.domain.hospital.entity.ENUM.HospitalType;
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
                                "inactiveHospitals", hospitals.countByStatus("INACTIVE"),
                                // 의료기관 화면 카드: 목록은 페이지 단위로만 불러오므로 전체 집계는 서버가 준다
                                "activeNightCare", hospitals.countByStatusAndNightCareTrue("ACTIVE"),
                                "activeEmergency", hospitals.countByStatusAndEmergencyTrue("ACTIVE"),
                                "activePharmacies",
                                        hospitals.countByStatusAndHospitalType("ACTIVE", HospitalType.PHARMACY)));
    }
}
