package skincare.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import skincare.dto.*;
import skincare.service.HospitalService;
import skincare.domain.hospital.entity.ENUM.HospitalType;
@RestController @RequestMapping("/api/wc/admin/hospitals") @RequiredArgsConstructor
public class AdminHospitalController {
    private final HospitalService service;
    @GetMapping public ResponseEntity<ServicePage<HospitalView>> search(@RequestParam(required=false) String q,@RequestParam(required=false) HospitalType type,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(q,type,null,page,size,true));}
    @PostMapping public ResponseEntity<HospitalView> create(@RequestBody HospitalRequest body){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(body));}
    @PutMapping("/{id}") public ResponseEntity<HospitalView> update(@PathVariable String id,@RequestBody HospitalRequest body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(id,body));}
}
