package skincare.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserSettings;
import skincare.dto.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.*;

import skincare.domain.user.entity.ENUM.UserStatus;

// 사용자(User) 관련 비즈니스 로직 처리
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository; // 사용자 데이터 베이스 접근용 Repository
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 및 검증용 객체
    private final UserSettingsRepository userSettingsRepository;
    private final AccountConsentService consents;
    private final AccountErasureService erasure;

    // 이메일 인증 코드 발송 (회원가입 전)
    @Transactional
    public void sendEmailVerificationCode(EmailVerificationRequestDto dto) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "구글·카카오·네이버 소셜 로그인을 이용해 주세요.");
    }

    // 이메일 인증 코드 검증
    @Transactional
    public void confirmEmailVerification(EmailVerificationConfirmRequestDto dto) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "구글·카카오·네이버 소셜 로그인을 이용해 주세요.");
    }

    // 사용자 회원가입 처리
    @Transactional
    public UserResponseDto signUp(UserSignUpRequestDto dto) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "구글·카카오·네이버 소셜 로그인을 이용해 주세요.");
    }

    // 사용자 로그인 처리 및 토큰 발급
    @Transactional(readOnly = true)
    public UserResponseDto login(UserLoginRequestDto dto) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "구글·카카오·네이버 소셜 로그인을 이용해 주세요.");
    }

    @Transactional(readOnly = true)
    public AccountProfile profile(String userId) {
        User u=userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        requireActive(u);
        return new AccountProfile(u.getUserId(),u.getNickname(),u.getEmail(),u.getProvider().name(),u.getPlan().name(),
            u.getTermsVersion(),u.getPrivacyVersion(),u.getConsentedAt());
    }
    @Transactional(readOnly = true)
    public UserSettingsResponseDto settings(String userId) {
        User u=userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)); requireActive(u);
        return userSettingsRepository.findById(userId).map(UserSettingsResponseDto::from)
            .orElse(new UserSettingsResponseDto(userId,true,true));
    }
    @Transactional
    public void changePassword(String userId,ChangePasswordRequest dto) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "구글·카카오·네이버 소셜 로그인을 이용해 주세요.");
    }

    // PUT /api/wc/users/me (사용자 프로필 수정)
    @Transactional
    public UserResponseDto updateProfile(String userId, UserProfileUpdateRequestDto dto) {
        User user = userRepository.lockForAnalysisCreation(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        requireActive(user);
        String nickname=dto.getNickname()==null ? null : ServiceInput.text(dto.getNickname(),50,"닉네임");
        String image=dto.getProfileImageUrl()==null ? null : ServiceInput.https(dto.getProfileImageUrl(),500);
        if(nickname==null && image==null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,"변경할 프로필 정보를 입력해 주세요.");
        user.updateProfile(nickname, image);

        return UserResponseDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .build();
    }

    // PUT /api/wc/users/me/settings (푸시 알림 및 환경설정 변경)
    @Transactional
    public UserSettingsResponseDto updateSettings(String userId, UserSettingsUpdateRequestDto dto) {
        User user=userRepository.lockForAnalysisCreation(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        requireActive(user);
        if(dto.getPushDiagnosisAlert()==null && dto.getPushRecoveryReminder()==null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,"변경할 설정을 선택해 주세요.");
        UserSettings settings = userSettingsRepository.findById(userId).orElseGet(() ->
            userSettingsRepository.save(UserSettings.builder().user(user).build()));

        settings.updateSettings(
                dto.getPushDiagnosisAlert(),
                dto.getPushRecoveryReminder()
        );

        return UserSettingsResponseDto.from(settings);
    }

    // DELETE /api/wc/users/me (회원 탈퇴)
    @Transactional
    public void withdraw(String userId, UserWithdrawRequestDto dto) {
        erasure.erase(userId,dto);
    }

    // POST /api/wc/auth/logout (로그아웃)
    @Transactional
    public void logout(String userId, LogoutRequestDto dto) {
        userRepository.lockForAnalysisCreation(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)).revokeTokens();
        refreshTokenRepository.deleteByUserId(userId);
    }

    // POST /api/wc/auth/password/reset (비밀번호 재설정 요청)
    @Transactional
    public void resetPasswordRequest(PasswordResetRequestDto dto) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "구글·카카오·네이버 소셜 로그인을 이용해 주세요.");
    }

    // 재설정 토큰 검증 후 비밀번호 변경
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequestDto dto) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
                "구글·카카오·네이버 소셜 로그인을 이용해 주세요.");
    }

    @Transactional
    public void acceptConsents(String userId, ConsentRequestDto dto) {
        consents.accept(userId,dto);
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN);
        }
    }

}
