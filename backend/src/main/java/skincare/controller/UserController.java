package skincare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import skincare.dto.*;
import skincare.service.UserService;

// 사용자(User) 관련 API 요청 처리 컨트롤러
@RestController
@RequestMapping("/api/wc")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService; // 사용자 비즈니스 로직 서비스

    // 이메일 인증 번호 발송 요청
    @PostMapping("/auth/email/send")
    public ResponseEntity<ApiResponseDto> sendEmailVerificationCode(@RequestBody EmailVerificationRequestDto requestDto) {
        userService.sendEmailVerificationCode(requestDto);
        return ApiResponseDto.ok("인증 번호가 이메일로 발송되었습니다.").toResponseEntity();
    }

    // 이메일 인증 번호 검증 요청
    @PostMapping("/auth/email/verify")
    public ResponseEntity<ApiResponseDto> confirmEmailVerification(@RequestBody EmailVerificationConfirmRequestDto requestDto) {
        userService.confirmEmailVerification(requestDto);
        return ApiResponseDto.ok("이메일 인증이 성공적으로 완료되었습니다.").toResponseEntity();
    }

    // 사용자 회원가입 API
    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponseDto> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {
        UserResponseDto userInfo = userService.signUp(requestDto);
        return ApiResponseDto.created("회원가입이 성공적으로 완료되었습니다.")
                .with("user_info", userInfo)
                .toResponseEntity();
    }

    // 사용자 로그인 API
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponseDto> login(@Valid @RequestBody UserLoginRequestDto requestDto) {
        LoginResponseDto<UserResponseDto> result = userService.login(requestDto);
        return ApiResponseDto.ok("로그인이 완료되었습니다.")
                .with("auth_token", result.authToken())
                .with("user_info", result.profile())
                .toResponseEntity();
    }

    // 사용자 로그아웃
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponseDto> logout(
            @AuthenticationPrincipal String userId,
            @RequestBody(required = false) LogoutRequestDto requestDto) {
        userService.logout(userId, requestDto);
        return ApiResponseDto.ok("성공적으로 로그아웃되었습니다.").toResponseEntity();
    }

    // 사용자 비밀번호 재설정
    @PostMapping("/auth/password/reset")
    public ResponseEntity<ApiResponseDto> resetPasswordRequest(@RequestBody PasswordResetRequestDto requestDto) {
        userService.resetPasswordRequest(requestDto);
        return ApiResponseDto.ok("입력하신 이메일로 비밀번호 재설정 링크가 발송되었습니다.").toResponseEntity();
    }

    // 비밀번호 변경
    @PostMapping("/auth/password/confirm")
    public ResponseEntity<ApiResponseDto> confirmPasswordReset(@RequestBody PasswordResetConfirmRequestDto requestDto) {
        userService.confirmPasswordReset(requestDto);
        return ApiResponseDto.ok("비밀번호가 성공적으로 변경되었습니다.").toResponseEntity();
    }

    // 사용자 프로필 변경 (multipart/form-data: nickname, profile_image 모두 선택 항목)
    @PutMapping(value = "/users/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto> updateProfile(
            @AuthenticationPrincipal String userId,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestPart(value = "profile_image", required = false) MultipartFile profileImage) {
        UserResponseDto response = userService.updateProfile(userId, nickname, profileImage);
        return ApiResponseDto.ok("사용자 프로필 정보가 성공적으로 수정되었습니다.")
                .with("updated_user_info", response)
                .toResponseEntity();
    }

    // 서버에 저장된 본인 프로필 사진 조회 (이미지 바이너리 응답)
    @GetMapping("/users/me/profile-image")
    public ResponseEntity<Resource> getProfileImage(@AuthenticationPrincipal String userId) {
        return userService.getProfileImage(userId).toResponseEntity();
    }

    // 사용자 환경설정 변경
    @PutMapping("/users/me/settings")
    public ResponseEntity<ApiResponseDto> updateSettings(
            @AuthenticationPrincipal String userId,
            @RequestBody UserSettingsUpdateRequestDto requestDto) {
        UserSettingsResponseDto response = userService.updateSettings(userId, requestDto);
        return ApiResponseDto.ok("서비스 환경설정이 성공적으로 변경되었습니다.")
                .with("updated_settings", response)
                .toResponseEntity();
    }

    // 사용자 회원탈퇴 (소셜 가입 회원은 비밀번호 없이 요청 가능)
    @DeleteMapping("/users/me")
    public ResponseEntity<ApiResponseDto> withdraw(
            @AuthenticationPrincipal String userId,
            @RequestBody(required = false) UserWithdrawRequestDto requestDto) {
        UserResponseDto withdrawalInfo = userService.withdraw(userId,
                requestDto != null ? requestDto : new UserWithdrawRequestDto());
        return ApiResponseDto.ok("회원 탈퇴 처리가 정상적으로 완료되었습니다. 그동안 서비스를 이용해 주셔서 감사합니다.")
                .with("withdrawal_info", withdrawalInfo)
                .toResponseEntity();
    }
}
