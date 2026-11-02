package skincare.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.dto.*;
import skincare.security.TokenService;
import skincare.service.AdminService;
import skincare.service.HospitalSyncService;

// 관리자(Admin) 관련 API 요청 처리 컨트롤러
@Slf4j
@RestController
@RequestMapping("/api/wc/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService; // 관리자 비즈니스 로직 서비스
    private final HospitalSyncService hospitalSyncService; // 의료기관 공공데이터 동기화 서비스
    private final TokenService tokenService; // 토큰 재발급 서비스

    // 관리자 계정 생성 API (기존 관리자만 호출 가능, SecurityConfig에서 ADMIN 권한 검사)
    @PostMapping("/signup")
    public ResponseEntity<ApiResponseDto> signUp(@Valid @RequestBody AdminSignUpRequestDto requestDto) {
        AdminResponseDto adminInfo = adminService.signUp(requestDto);
        return ApiResponseDto.created("관리자 계정이 생성되었습니다.")
                .with("admin_info", adminInfo)
                .toResponseEntity();
    }

    // 관리자 로그인 API
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponseDto> login(@Valid @RequestBody AdminLoginRequestDto requestDto) {
        LoginResponseDto<AdminResponseDto> result = adminService.login(requestDto);
        return ApiResponseDto.ok("관리자 로그인이 완료되었습니다.")
                .with("auth_token", result.authToken())
                .with("admin_info", result.profile())
                .toResponseEntity();
    }

    // 관리자 액세스 토큰 재발급 API (리프레시 토큰 Rotation)
    @PostMapping("/auth/token/refresh")
    public ResponseEntity<ApiResponseDto> refreshToken(@Valid @RequestBody TokenRefreshRequestDto requestDto) {
        return ApiResponseDto.ok("토큰이 재발급되었습니다.")
                .with("auth_token", tokenService.reissue(requestDto.getRefreshToken(), "ADMIN"))
                .toResponseEntity();
    }

    // 관리자 로그아웃 API (refresh_token 전달 시 해당 토큰만, 미전달 시 모든 기기 로그아웃)
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponseDto> logout(@AuthenticationPrincipal String adminId,
                                                 @RequestBody(required = false) LogoutRequestDto requestDto) {
        adminService.logout(adminId, requestDto != null ? requestDto.getRefreshToken() : null);
        return ApiResponseDto.ok("관리자 로그아웃이 완료되었습니다.").toResponseEntity();
    }

    // 관리자 프로필 조회 API
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto> getMyProfile(@AuthenticationPrincipal String adminId) {
        return ApiResponseDto.ok("관리자 프로필 조회가 완료되었습니다.")
                .with("admin_info", adminService.getMyProfile(adminId))
                .toResponseEntity();
    }

    // 관리자 비밀번호 변경 API
    @PutMapping("/password")
    public ResponseEntity<ApiResponseDto> changePassword(
            @AuthenticationPrincipal String adminId,
            @RequestBody AdminPasswordChangeRequestDto requestDto) {
        adminService.changePassword(adminId, requestDto);
        return ApiResponseDto.ok("관리자 비밀번호가 성공적으로 변경되었습니다.").toResponseEntity();
    }

    // 병원/약국 공공데이터 동기화 실행 API
    @Operation(
            summary = "전국 피부과 병원 및 약국 데이터 동기화",
            description = "공공데이터 포털의 병의원/약국 데이터셋과 국립중앙의료원 응급실 정보 API를 연동하여 DB를 최신화합니다. "
                    + "target_scope: ALL(기본), HOSPITAL, PHARMACY"
    )
    @PostMapping("/hospitals/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto> syncHospitals(
            @RequestBody(required = false) HospitalSyncRequestDto requestDto
    ) {
        if (requestDto == null) {
            requestDto = new HospitalSyncRequestDto();
        }

        log.info("[AdminController] 병원/약국 공공데이터 동기화 요청 수신 (TargetScope: {})", requestDto.getTargetScope());

        HospitalSyncResponseDto response = hospitalSyncService.syncHospitals(requestDto);

        return ApiResponseDto.ok("공공데이터 OpenAPI 의료기관 동기화가 성공적으로 완료되었습니다.")
                .with("sync_summary", response)
                .toResponseEntity();
    }
}
