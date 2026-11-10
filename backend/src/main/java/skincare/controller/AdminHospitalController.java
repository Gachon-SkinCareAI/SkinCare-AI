package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.server.ResponseStatusException;

import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.dto.*;
import skincare.service.AdminAuditService;
import skincare.service.HospitalService;
import skincare.service.HospitalSyncService;

import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/wc/admin/hospitals")
@RequiredArgsConstructor
public class AdminHospitalController {

    private final HospitalService service;
    private final HospitalSyncService syncService;
    private final AdminAuditService audit;

    // 동기화는 수만 건을 한 트랜잭션으로 갱신하므로 동시에 하나만 실행한다 (서버 1대 기준)
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    // 병원 조회 (이름순, 한 페이지 최대 50건). status는 ACTIVE(운영 중) 또는 INACTIVE(운영 중지·숨김)
    @GetMapping
    public ResponseEntity<ServicePage<HospitalView>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) HospitalType type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.search(q, type, null, status, page, size, true));
    }

    // 병원 생성
    @PostMapping
    public ResponseEntity<HospitalView> create(@RequestBody HospitalRequest body) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(service.create(body));
    }

    // 공공데이터 기반 의료기관 동기화 (본문 생략 시 병원+약국 전체)
    @PostMapping("/sync")
    public ResponseEntity<HospitalSyncResponseDto> sync(
            @RequestBody(required = false) HospitalSyncRequestDto body) {
        if (!syncing.compareAndSet(false, true))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "동기화가 이미 진행 중입니다. 끝난 뒤 다시 시도해 주세요.");
        try {
            var result = syncService.syncHospitals(body == null ? new HospitalSyncRequestDto() : body);
            var m = result.getMetrics();
            audit.current(
                    "HOSPITAL_SYNC",
                    result.getSyncId(),
                    result.getTargetScope()
                            + " +"
                            + m.getNewAdded()
                            + " ~"
                            + m.getUpdated()
                            + " -"
                            + m.getDeactivated());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        } finally {
            syncing.set(false);
        }
    }

    // 병원 수정
    @PutMapping("/{id}")
    public ResponseEntity<HospitalView> update(
            @PathVariable String id, @RequestBody HospitalRequest body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.update(id, body));
    }
}
