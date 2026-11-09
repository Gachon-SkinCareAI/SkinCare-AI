package skincare.controller;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import skincare.service.AccountConsentService;
import skincare.service.AccountErasureService;
import skincare.service.ConsentPolicy;
import skincare.service.GuestSessionService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
// GuestController API 요청 처리
public class GuestController {
    private final GuestSessionService guestSessionService;
    private final AccountConsentService consents;
    private final AccountErasureService erasureService;

    // 제한된 범위 내에서 일시적으로만 유지되는 요청 카운터
    private final Map<String, Window> starts = new LinkedHashMap<>();

    private record Window(Instant from, int count) {}

    // 주소정보 저장안함
    private synchronized void limit(String address) {
        Instant now = Instant.now();
        starts.entrySet().removeIf(e -> e.getValue().from().plusSeconds(3600).isBefore(now));

        var w = starts.get(address);
        if (w != null && w.count() >= 30) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "비회원 시작 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (w == null && starts.size() >= 4096) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        }

        starts.put(address, new Window(w == null ? now : w.from(), w == null ? 1 : w.count() + 1));
    }

    //
    @GetMapping("/api/wc/auth/guest/policy")
    public ResponseEntity<?> policy() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        Map.of(
                                "version",
                                ConsentPolicy.GUEST_VERSION,
                                "serviceUrl",
                                "/api/wc/policies/2026-11-04/guest",
                                "healthUrl",
                                "/api/wc/policies/2026-11-04/guest#health-data",
                                "trainingVersion",
                                ConsentPolicy.TRAINING_VERSION,
                                "trainingUrl",
                                "/api/wc/policies/2026-11-04/training",
                                "retentionYears",
                                5,
                                "trainingExportEnabled",
                                false));
    }

    @PostMapping("/api/wc/auth/guest")
    public ResponseEntity<?> start(
            @RequestBody GuestSessionService.Start body, HttpServletRequest request) {
        limit(request.getRemoteAddr());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(guestSessionService.start(body));
    }

    @PostMapping("/api/wc/auth/guest/resume")
    public ResponseEntity<?> resume(@RequestBody GuestSessionService.Resume body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(guestSessionService.resume(body));
    }

    @GetMapping("/api/wc/guest/consents")
    public ResponseEntity<?> state(@AuthenticationPrincipal String user) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(consents.state(user, 0, 20));
    }

    @PutMapping("/api/wc/guest/consents/training")
    public ResponseEntity<?> training(
            @AuthenticationPrincipal String user,
            @RequestBody AccountConsentService.TrainingRequest body) {
        consents.training(user, body);
        return ResponseEntity.noContent().build();
    }

    public record DeleteRequest(String confirmation) {}

    @DeleteMapping("/api/wc/guest")
    public ResponseEntity<?> delete(
            @AuthenticationPrincipal String user, @RequestBody DeleteRequest body) {
        if (!"삭제".equals(body.confirmation()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제를 입력해 주세요.");
        var d = new skincare.dto.UserWithdrawRequestDto();
        d.setConfirmation("탈퇴");
        erasureService.erase(user, d);
        return ResponseEntity.noContent().build();
    }
}
