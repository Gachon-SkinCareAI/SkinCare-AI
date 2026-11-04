package skincare.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Read-only draft documents. These routes never authorize consent or publish a policy. */
@RestController
public class PolicyPreviewController {
    @GetMapping(value="/api/wc/policy-previews/terms",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> terms() throws IOException {return document("terms");}
    @GetMapping(value="/api/wc/policy-previews/privacy",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> privacy() throws IOException {return document("privacy");}
    private ResponseEntity<String> document(String name) throws IOException {
        String html=new ClassPathResource("policy-previews/"+name+".html").getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
            .header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow")
            .contentType(new MediaType("text","html",StandardCharsets.UTF_8)).body(html);
    }
}
