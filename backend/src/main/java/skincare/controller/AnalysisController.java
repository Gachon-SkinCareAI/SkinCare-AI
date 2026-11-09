package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import skincare.dto.*;
import skincare.service.*;

import java.net.URI;
import java.util.*;

//
@RestController
@RequestMapping("/api/wc/analyses")
@RequiredArgsConstructor
// AnalysisController API 요청 처리
public class AnalysisController {
    private final AnalysisService service;

    //
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal String user,
            @RequestPart("file") MultipartFile file,
            @RequestParam String clientRequestId,
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) String capturedOn) {
        var result =
                service.create(
                        user, clientRequestId, parentId, AnalysisImage.read(file), capturedOn);
        return ResponseEntity.created(URI.create("/api/wc/analyses/" + result.get("id")))
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @AuthenticationPrincipal String user,
            @RequestParam(required = false) String caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(user, caseId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @AuthenticationPrincipal String user, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(user, id));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(
            @AuthenticationPrincipal String user, @PathVariable String id) {
        var image = service.image(user, id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<Map<String, Object>> answer(
            @AuthenticationPrincipal String user,
            @PathVariable String id,
            @RequestBody AnalysisAnswerRequest body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.answer(user, id, body));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(
            @AuthenticationPrincipal String user,
            @PathVariable String id,
            @RequestBody AnalysisRetryRequest body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.retry(user, id, body.expectedVersion()));
    }

    @PutMapping("/{id}/progress")
    public ResponseEntity<Map<String, Object>> progress(
            @AuthenticationPrincipal String user,
            @PathVariable String id,
            @RequestBody AnalysisProgressRequest body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.progress(user, id, body));
    }

    @GetMapping("/{id}/comparison")
    public ResponseEntity<Map<String, Object>> comparison(
            @AuthenticationPrincipal String user,
            @PathVariable String id,
            @RequestParam(required = false) String referenceId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.comparison(user, id, referenceId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal String user, @PathVariable String id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
