package skincare.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import skincare.service.PublishedPolicies;
@RestController @RequiredArgsConstructor
public class ConsentPolicyController {
    private final PublishedPolicies policies;
    @GetMapping("/api/wc/auth/consent-policy") public ResponseEntity<PublishedPolicies.Manifest> policy() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(policies.manifest());
    }
}
