package skincare.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.dto.*;
import skincare.service.AdminService;

// 관리자(Admin) 관련 API 요청 처리 컨트롤러
@RestController
@RequestMapping("/api/wc/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드(React/Vite) CORS 허용
public class AdminController {

    private final AdminService adminService; // 관리자 비즈니스 로직 서비스

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
}