package skincare.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Versioned local service documents. Consent availability is configured separately. */
@RestController
public class PolicyDocumentController {
    @GetMapping(value="/api/wc/policies/2026-11-01.2/terms",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> oldTerms() throws IOException {return document("2026-11-01.2","terms");}
    @GetMapping(value="/api/wc/policies/2026-11-01.2/privacy",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> oldPrivacy() throws IOException {return document("2026-11-01.2","privacy");}
    @GetMapping(value="/api/wc/policies/2026-11-03/terms",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> terms() throws IOException {return document("2026-11-03","terms");}
    @GetMapping(value="/api/wc/policies/2026-11-03/privacy",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> privacy() throws IOException {return document("2026-11-03","privacy");}
    @GetMapping(value="/api/wc/policies/2026-11-04/{name:guest|training|location}",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> dataUse(@PathVariable String name) throws IOException {return document("2026-11-04",name);}
    private ResponseEntity<String> document(String version,String name) throws IOException {
        String html=new ClassPathResource("policies/"+version+"/"+name+".html").getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
            .header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow")
            .contentType(new MediaType("text","html",StandardCharsets.UTF_8)).body(html);
    }
}
