package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.dto.HospitalView;
import skincare.dto.ServicePage;
import skincare.service.HospitalService;

@RestController
@RequestMapping("/api/wc/hospitals")
@RequiredArgsConstructor
// HospitalController API 요청 처리
public class HospitalController {
    private final HospitalService service;

    @GetMapping
    public ResponseEntity<ServicePage<HospitalView>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) HospitalType type,
            @RequestParam(required = false) Boolean emergency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.search(q, type, emergency, page, size, false));
    }
}
