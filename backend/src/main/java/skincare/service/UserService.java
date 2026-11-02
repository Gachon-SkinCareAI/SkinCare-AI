package skincare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import skincare.domain.global.IdGenerator;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.EmailVerificationToken;
import skincare.domain.user.entity.PasswordResetToken;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserSettings;
import skincare.dto.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.*;
import skincare.security.AccountStatusChecker;
import skincare.security.TokenService;
import skincare.security.oauth.GoogleIdTokenVerifier;
import skincare.security.oauth.GoogleUserRegistrar;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// 사용자(User) 관련 비즈니스 로직 처리
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom(); // 이메일 인증 코드 생성용 난수 생성기

    private final UserRepository userRepository; // 사용자 데이터 베이스 접근용 Repository
    private final PasswordResetTokenRepository passwordResetTokenRepository; // 비밀번호 재설정 토큰 데이터 베이스 접근용 Repository
    private final EmailVerificationTokenRepository emailVerificationTokenRepository; // 이메일 인증 토큰 데이터 베이스 접근용 Repository
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 및 검증용 객체
    private final UserSettingsRepository userSettingsRepository; // 사용자 환경설정 데이터 베이스 접근용 Repository
    private final TokenService tokenService; // Access/Refresh Token 발급 및 폐기 서비스
    private final JavaMailSender mailSender; // 인증/재설정 이메일 발송용 객체
    private final FileStorageService fileStorageService; // 프로필 사진 파일 저장소
    private final UserActivityService userActivityService; // 사용자 활동 요약 집계 서비스
    private final GoogleIdTokenVerifier googleIdTokenVerifier; // Google ID Token 검증
    private final GoogleUserRegistrar googleUserRegistrar; // 구글 회원 조회/등록

    // 이메일 인증 코드 발송 (회원가입 전)
    @Transactional
    public void sendEmailVerificationCode(EmailVerificationRequestDto dto) {
        // 이미 가입된 이메일인지 확인
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이미 가입된 이메일 주소입니다.");
        }

        // 6자리 난수 인증코드 생성
        String authCode = createRandomAuthCode();

        // 5분 유효기간 설정
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findById(dto.getEmail())
                .map(token -> {
                    token.updateAuthCode(authCode, LocalDateTime.now().plusMinutes(5));
                    return token;
                })
                .orElseGet(() -> EmailVerificationToken.builder()
                        .email(dto.getEmail())
                        .authCode(authCode)
                        .expirationTime(LocalDateTime.now().plusMinutes(5))
                        .build());

        emailVerificationTokenRepository.save(verificationToken);

        // 이메일 전송
        sendVerificationEmail(dto.getEmail(), authCode);
    }

    // 이메일 인증 코드 검증
    @Transactional
    public void confirmEmailVerification(EmailVerificationConfirmRequestDto dto) {
        EmailVerificationToken token = emailVerificationTokenRepository.findById(dto.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN, "인증 요청 내역이 존재하지 않습니다."));

        if (token.isExpired()) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED, "인증 코드가 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!token.getAuthCode().equals(dto.getAuthCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 일치하지 않습니다.");
        }

        // 인증 성공 상태 변경
        token.completeVerification();
    }

    // 사용자 회원가입 처리
    // 회원 아이디는 서버에서 생성 (ex. USR-20261101-153022-3fa85f6457a1), 로그인은 이메일로 수행
    @Transactional
    public UserResponseDto signUp(UserSignUpRequestDto dto) {

        // 필수 약관 동의 여부 검증
        if (!dto.isAgreedTerms() || !dto.isAgreedPrivacy()) {
            throw new CustomException(ErrorCode.TERMS_NOT_AGREED);
        }

        // 이메일 중복 확인 (탈퇴 계정 포함, DB unique 제약 위반으로 500 오류가 나지 않도록 사전 검증)
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // 이메일 인증 완료 여부 검증
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByEmailAndVerifiedTrue(dto.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_VERIFIED));

        // 사용자 엔티티 생성
        // 비밀번호는 암호화
        User user = User.builder()
                .userId(IdGenerator.generate("USR"))
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
                .agreedTerms(dto.isAgreedTerms())
                .agreedPrivacy(dto.isAgreedPrivacy())
                .build();

        // 기본 환경설정 자동 생성
        UserSettings settings = UserSettings.builder()
                .user(user)
                .build();

        user.initSettings(settings);

        // DB에 사용자 정보 저장
        userRepository.save(user);

        // 사용한 인증 토큰 삭제 처리
        emailVerificationTokenRepository.delete(verificationToken);

        log.info("[Auth] 회원가입 완료 (user_id: {})", user.getUserId());

        // 응답 DTO 변환 및 반환
        return UserResponseDto.from(user);
    }

    // 사용자 로그인 처리 및 토큰 발급
    @Transactional
    public LoginResponseDto<UserResponseDto> login(UserLoginRequestDto dto) {

        // 입력받은 이메일로 사용자 조회
        // 계정 존재 여부가 노출되지 않도록 미가입/비밀번호 불일치 모두 동일한 오류(AUTH-001) 반환
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        // 비밀번호 일치 여부 검증 (소셜 가입 계정은 비밀번호가 없으므로 불일치 처리)
        if (user.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("[Auth] 로그인 실패 - 이메일 또는 비밀번호 불일치 (email: {})", dto.getEmail());
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 탈퇴/정지 계정 로그인 차단 (비밀번호 검증 이후에 확인하여 계정 상태 노출 방지)
        ErrorCode blockedReason = AccountStatusChecker.resolveBlockedReason(user.getStatus());
        if (blockedReason != null) {
            throw new CustomException(blockedReason);
        }

        // 최종 접속 일시 갱신
        user.updateLastAccess();

        // 인증 성공 시 JWT 토큰 생성
        AuthTokenDto authToken = tokenService.issue(user.getUserId(), "USER");

        log.info("[Auth] 일반 로그인 성공 (user_id: {})", user.getUserId());

        // 토큰 및 사용자 정보를 포함한 응답 DTO 반환
        return new LoginResponseDto<>(authToken, UserResponseDto.from(user));
    }

    // POST /api/wc/auth/social/google (모바일 Google ID Token 로그인/회원가입)
    @Transactional
    public LoginResponseDto<UserResponseDto> googleLogin(GoogleLoginRequestDto dto) {
        GoogleIdTokenVerifier.GoogleUserInfo googleUser = googleIdTokenVerifier.verify(dto.getIdToken());
        User user = googleUserRegistrar.findOrRegister(
                googleUser.email(), googleUser.name(), googleUser.picture(), googleUser.sub());

        AuthTokenDto authToken = tokenService.issue(user.getUserId(), "USER");
        log.info("[Auth] 정상 처리, Google 소셜 로그인 완료 (user_id: {}, provider: GOOGLE)", user.getUserId());
        return new LoginResponseDto<>(authToken, UserResponseDto.from(user));
    }

    // GET /api/wc/users/me (로그인한 사용자 프로필, 구독 정보, 활동 요약 조회)
    @Transactional(readOnly = true)
    public Map<String, Object> getMyInfo(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 별도 결제 시스템이 없으므로 구독 정보는 회원의 이용 플랜으로 구성 (결제 연동 시 next_billing_date 제공)
        Map<String, Object> subscription = new LinkedHashMap<>();
        subscription.put("plan_type", user.getPlan());
        subscription.put("status", "ACTIVE");
        subscription.put("next_billing_date", null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_info", UserResponseDto.from(user));
        result.put("subscription_info", subscription);
        result.put("activity_summary", userActivityService.summarize(userId));
        return result;
    }

    // PUT /api/wc/users/me (사용자 프로필 수정 - 닉네임, 프로필 사진)
    @Transactional
    public UserResponseDto updateProfile(String userId, String nickname, MultipartFile profileImage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 새 프로필 사진이 있으면 저장 후 기존 서버 저장 사진 삭제
        String newImageKey = null;
        String oldImage = user.getProfileImageUrl();
        if (profileImage != null && !profileImage.isEmpty()) {
            newImageKey = fileStorageService.storeImage(profileImage, "profiles");
        }

        user.updateProfile(StringUtils.hasText(nickname) ? nickname.trim() : null, newImageKey);

        if (newImageKey != null && FileStorageService.isStoredFile(oldImage)) {
            fileStorageService.deleteQuietly(oldImage);
        }

        log.info("[Auth] 회원 프로필 정보 수정 완료 (user_id: {})", userId);
        return UserResponseDto.from(user);
    }

    // GET /api/wc/users/me/profile-image (서버에 저장된 본인 프로필 사진 조회)
    @Transactional(readOnly = true)
    public StoredFileDto getProfileImage(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String imageKey = user.getProfileImageUrl();
        if (!FileStorageService.isStoredFile(imageKey)) {
            throw new CustomException(ErrorCode.IMAGE_FILE_NOT_FOUND);
        }
        return new StoredFileDto(fileStorageService.load(imageKey), fileStorageService.getMediaType(imageKey));
    }

    // PUT /api/wc/users/me/settings (푸시 알림 및 환경설정 변경)
    @Transactional
    public UserSettingsResponseDto updateSettings(String userId, UserSettingsUpdateRequestDto dto) {
        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        settings.updateSettings(
                dto.getPushDiagnosisAlert(),
                dto.getPushRecoveryReminder()
        );

        return UserSettingsResponseDto.from(settings);
    }

    // DELETE /api/wc/users/me (회원 탈퇴)
    @Transactional
    public UserResponseDto withdraw(String userId, UserWithdrawRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 일반 회원가입 유저인 경우 비밀번호 검증
        if (user.getProvider() == Provider.LOCAL) {
            if (dto.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
            }
        }

        // 탈퇴 시 DB Refresh Token도 함께 삭제
        tokenService.revokeAll(userId, "USER");
        user.withdraw();

        log.info("[Auth] 회원 탈퇴 완료 및 계정 비활성화 (user_id: {}, reason: {})", userId, dto.getReason());
        return UserResponseDto.from(user).toBuilder()
                .deletedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")))
                .build();
    }

    // POST /api/wc/auth/logout (로그아웃)
    // 파생 delete 쿼리(deleteByUserId)는 트랜잭션 내에서만 실행 가능
    @Transactional
    public void logout(String userId, LogoutRequestDto dto) {
        // 전달받은 Refresh Token만 폐기 (토큰 미전달 시 모든 기기에서 로그아웃)
        tokenService.revoke(userId, "USER", dto != null ? dto.getRefreshToken() : null);
    }

    // POST /api/wc/auth/password/reset (비밀번호 재설정 요청)
    @Transactional
    public void resetPasswordRequest(PasswordResetRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 소셜 가입 사용자는 비밀번호 재설정 불가
        if (user.getProvider() != Provider.LOCAL) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "소셜 로그인 사용자는 비밀번호를 재설정할 수 없습니다.");
        }

        // 30분 유효기간을 가진 일회성 재설정 토큰 생성
        String resetTokenId = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .tokenId(resetTokenId)
                .email(user.getEmail())
                .expirationTime(LocalDateTime.now().plusMinutes(30))
                .build();

        passwordResetTokenRepository.save(resetToken);

        // 재설정 링크 이메일 발송
        String resetLink = "http://localhost:5173/reset-password?token=" + resetTokenId;
        sendResetEmail(user.getEmail(), resetLink);
    }

    // 재설정 토큰 검증 후 비밀번호 변경
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequestDto dto) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenIdAndUsedFalse(dto.getResetToken())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 이미 사용된 재설정 토큰입니다."));

        if (resetToken.isExpired()) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED, "만료된 재설정 토큰입니다. 다시 신청해 주세요.");
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 변경 및 재설정 토큰 사용 완료 처리
        user.updatePassword(passwordEncoder.encode(dto.getNewPassword()));
        resetToken.useToken();
    }

    // 6자리 숫자 인증 코드 생성 (예측 불가능한 SecureRandom 사용)
    private String createRandomAuthCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    // 회원가입 이메일 인증 번호 메일 발송
    private void sendVerificationEmail(String toEmail, String authCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[SkinCare AI] 회원가입 이메일 인증 번호 안내");
        message.setText("안녕하세요. SkinCare AI 서비스 회원가입 인증 번호는 [" + authCode + "] 입니다.\n\n(5분 이내에 입력해 주세요.)");
        mailSender.send(message);
    }

    // 비밀번호 재설정 링크 메일 발송
    private void sendResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[SkinCare AI] 비밀번호 재설정 안내");
        message.setText("안녕하세요. 아래 링크를 클릭하여 비밀번호를 재설정해 주세요.\n\n" + resetLink + "\n\n(해당 링크는 30분간 유효합니다.)");
        mailSender.send(message);
    }
}