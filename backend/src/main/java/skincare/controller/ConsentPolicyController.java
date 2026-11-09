package skincare.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import skincare.service.PublishedPolicies;

@RestController
@RequiredArgsConstructor
// ConsentPolicyController API 요청 처리
public class ConsentPolicyController {
    private final PublishedPolicies policies;

    @GetMapping("/api/wc/auth/consent-policy")
    public ResponseEntity<PublishedPolicies.Manifest> policy() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(policies.manifest());
    }
}
