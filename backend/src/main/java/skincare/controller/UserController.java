package skincare.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import skincare.dto.*;
import skincare.service.UserService;

// 사용자(User) 관련 API 요청 처리 컨트롤러
@RestController
@RequestMapping("/api/wc")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드(React/Vite) CORS 허용
public class UserController {

    private final UserService userService; // 사용자 비즈니스 로직 서비스

    // 이메일 인증 번호 발송 요청
    @PostMapping("/auth/email/send")
    public ResponseEntity<ApiResponseDto<Void>> sendEmailVerificationCode(@RequestBody EmailVerificationRequestDto requestDto) {
        userService.sendEmailVerificationCode(requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("인증 번호가 이메일로 발송되었습니다."));
    }

    // 이메일 인증 번호 검증 요청
    @PostMapping("/auth/email/verify")
    public ResponseEntity<ApiResponseDto<Void>> confirmEmailVerification(@RequestBody EmailVerificationConfirmRequestDto requestDto) {
        userService.confirmEmailVerification(requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("이메일 인증이 성공적으로 완료되었습니다."));
    }

    // 사용자 회원가입 API
    @PostMapping("/auth/signup")
    public ResponseEntity<UserResponseDto> signUp(@RequestBody UserSignUpRequestDto requestDto) {
        return ResponseEntity.ok(userService.signUp(requestDto));
    }

    // 사용자 로그인 API
    @PostMapping("/auth/login")
    public ResponseEntity<UserResponseDto> login(@RequestBody UserLoginRequestDto requestDto) {
        return ResponseEntity.ok(userService.login(requestDto));
    }

    // 사용자 로그아웃
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(
            @AuthenticationPrincipal String userId,
            @RequestBody LogoutRequestDto requestDto) {
        userService.logout(userId, requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("성공적으로 로그아웃되었습니다."));
    }

    // 사용자 비밀번호 재설정
    @PostMapping("/auth/password/reset")
    public ResponseEntity<ApiResponseDto<Void>> resetPasswordRequest(@RequestBody PasswordResetRequestDto requestDto) {
        userService.resetPasswordRequest(requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("비밀번호 재설정 이메일이 발송되었습니다."));
    }

    // 비밀번호 변경
    @PostMapping("/auth/password/confirm")
    public ResponseEntity<ApiResponseDto<Void>> confirmPasswordReset(@RequestBody PasswordResetConfirmRequestDto requestDto) {
        userService.confirmPasswordReset(requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("비밀번호가 성공적으로 변경되었습니다."));
    }

    // 사용자 프로필 변경
    @PutMapping("/users/me")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> updateProfile(
            @AuthenticationPrincipal String userId,
            @RequestBody UserProfileUpdateRequestDto requestDto) {
        UserResponseDto response = userService.updateProfile(userId, requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("프로필 정보가 성공적으로 수정되었습니다.", response));
    }

    // 사용자 환경설정 변경
    @PutMapping("/users/me/settings")
    public ResponseEntity<ApiResponseDto<UserSettingsResponseDto>> updateSettings(
            @AuthenticationPrincipal String userId,
            @RequestBody UserSettingsUpdateRequestDto requestDto) {
        UserSettingsResponseDto response = userService.updateSettings(userId, requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("서비스 환경설정이 변경되었습니다.", response));
    }

    // 사용자 회원탈퇴
    @DeleteMapping("/users/me")
    public ResponseEntity<ApiResponseDto<Void>> withdraw(
            @AuthenticationPrincipal String userId,
            @RequestBody UserWithdrawRequestDto requestDto) {
        userService.withdraw(userId, requestDto);
        return ResponseEntity.ok(ApiResponseDto.success("회원 탈퇴 처리가 완료되었습니다."));
    }
}