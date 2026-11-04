package skincare.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import skincare.dto.*;
import skincare.service.HospitalService;
import skincare.domain.hospital.entity.ENUM.HospitalType;
@RestController @RequestMapping("/api/wc/hospitals") @RequiredArgsConstructor
public class HospitalController {
    private final HospitalService service;
    @GetMapping public ResponseEntity<ServicePage<HospitalView>> search(@RequestParam(required=false) String q,@RequestParam(required=false) HospitalType type,@RequestParam(required=false) Boolean emergency,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(q,type,emergency,page,size,false));
    }
}
