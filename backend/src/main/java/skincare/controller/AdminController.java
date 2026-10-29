package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.dto.*;
import skincare.service.AdminService;
//import skincare.service.HospitalService;
import skincare.service.HospitalSyncService;

// 관리자(Admin) 관련 API 요청 처리 컨트롤러
@Slf4j
@RestController
@RequestMapping("/api/wc/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드(React/Vite) CORS 허용
public class AdminController {

    private final AdminService adminService; // 관리자 비즈니스 로직 서비스
    private final HospitalSyncService hospitalSyncService;
//    private final HospitalService hospitalService;

    // 관리자 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<AdminResponseDto> signUp(@RequestBody AdminSignUpRequestDto requestDto) {
        return ResponseEntity.ok(adminService.signUp(requestDto));
    }

    // 관리자 로그인 API
    @PostMapping("/auth/login")
    public ResponseEntity<AdminResponseDto> login(@RequestBody AdminLoginRequestDto requestDto) {
        return ResponseEntity.ok(adminService.login(requestDto));
    }

    // 관리자 로그아웃 API
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(@AuthenticationPrincipal String adminId) {
        adminService.logout(adminId);
        return ResponseEntity.ok(ApiResponseDto.success("관리자 로그아웃이 완료되었습니다."));
    }

    // 관리자 비밀번호 변경 API
    @PutMapping("/password")
    public ResponseEntity<ApiResponseDto<Void>> changePassword(
            @AuthenticationPrincipal String adminId,
            @RequestBody AdminPasswordChangeRequestDto requestDto) {
        adminService.changePassword(adminId, requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("관리자 비밀번호가 성공적으로 변경되었습니다."));
    }

    // 공공데이터 OpenAPI 의료기관 데이터 동기화 실행 API
//    @PostMapping("/hospitals/sync")
//    public ResponseEntity<ApiResponseDto<HospitalSyncResponseDto>> syncHospitals(
//            @RequestBody HospitalSyncRequestDto requestDto) {
//        HospitalSyncResponseDto response = hospitalService.syncHospitalsFromOpenApi(requestDto);
//        return ResponseEntity.ok(ApiResponseDto.success("공공데이터 OpenAPI 의료기관 동기화가 완료되었습니다.", response));
//    }

    @Operation(
            summary = "전국 피부과 병원 및 약국 데이터 동기화",
            description = "공공데이터 포털의 병의원/약국 데이터셋과 국립중앙의료원 응급실 정보 API를 연동하여 DB를 최신화합니다."
    )
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')") // 관리자 권한 체크 (시큐리티 설정에 맞춰 조정)
    public ResponseEntity<HospitalSyncResponseDto> syncHospitals(
            @RequestBody(required = false) HospitalSyncRequestDto requestDto
    ) {
        if (requestDto == null) {
            requestDto = new HospitalSyncRequestDto();
        }

        log.info("[AdminController] 병원/약국 공공데이터 동기화 요청 수신 (TargetScope: {})", requestDto.getTargetScope());

        HospitalSyncResponseDto response = hospitalSyncService.syncHospitals(requestDto);

        return ResponseEntity.ok(response);
    }
}