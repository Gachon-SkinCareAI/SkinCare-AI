package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import skincare.external.PublicMedicalClient;

@RestController
@RequestMapping("/api/wc/hospitals/public")
@RequiredArgsConstructor
// PublicMedicalController API 요청 처리
public class PublicMedicalController {
    private final PublicMedicalClient client;
    private final skincare.service.AccountConsentService consents;

    public record NearbyRequest(
            PublicMedicalClient.Kind kind,
            Double latitude,
            Double longitude,
            Integer page,
            Integer size) {}

    // Coordinates stay out of URLs/access logs and are never persisted.
    @PostMapping("/nearby")
    public ResponseEntity<PublicMedicalClient.Result> nearby(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String user,
            @RequestBody NearbyRequest body) {
        consents.requireLocation(user);
        int page = body.page() == null ? 0 : body.page(),
                size = body.size() == null ? 20 : body.size();
        if (body.kind() == null
                || body.latitude() == null
                || body.longitude() == null
                || !Double.isFinite(body.latitude())
                || !Double.isFinite(body.longitude())
                || Math.abs(body.latitude()) > 90
                || Math.abs(body.longitude()) > 180
                || page < 0
                || page > 10000
                || size < 1
                || size > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "위치와 페이지 크기를 확인해 주세요.");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        client.search(
                                new PublicMedicalClient.Query(
                                        body.kind(),
                                        "",
                                        "",
                                        "",
                                        page,
                                        size,
                                        body.latitude(),
                                        body.longitude())));
    }

    @GetMapping
    public ResponseEntity<PublicMedicalClient.Result> search(
            @RequestParam PublicMedicalClient.Kind kind,
            @RequestParam(defaultValue = "") String sido,
            @RequestParam(defaultValue = "") String sigungu,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0
                || page > 10000
                || size < 1
                || size > 50
                || sido.length() > 30
                || sigungu.length() > 60
                || name.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색 조건과 페이지 크기를 확인해 주세요.");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        client.search(
                                new PublicMedicalClient.Query(
                                        kind,
                                        sido.strip(),
                                        sigungu.strip(),
                                        name.strip(),
                                        page,
                                        size)));
    }
}
