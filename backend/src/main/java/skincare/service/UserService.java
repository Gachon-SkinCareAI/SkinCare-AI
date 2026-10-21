package skincare.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.EmailVerificationToken;
import skincare.domain.user.entity.PasswordResetToken;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserSettings;
import skincare.dto.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

// 사용자(User) 관련 비즈니스 로직 처리
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository; // 사용자 데이터 베이스 접근용 Repository
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 및 검증용 객체
    private final UserSettingsRepository userSettingsRepository;
    private final JwtTokenProvider jwtTokenProvider; // JWT 토큰 생성 및 검증용 객체
    private final JavaMailSender mailSender;

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
            throw new CustomException(ErrorCode.EXPIRED_TOKEN, "인증 코드가 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!token.getAuthCode().equals(dto.getAuthCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 일치하지 않습니다.");
        }

        // 인증 성공 상태 변경
        token.completeVerification();
    }

    // 사용자 회원가입 처리
    @Transactional
    public UserResponseDto signUp(UserSignUpRequestDto dto) {

        // 사용자 아이디 중복 확인
        if (userRepository.existsById(dto.getUserId())) {
            throw new CustomException(ErrorCode.DUPLICATE_USER_ID);
        }

        // 이메일 인증 완료 여부 검증
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByEmailAndVerifiedTrue(dto.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이메일 인증이 완료되지 않았습니다."));

        // 사용자 엔티티 생성
        // 비밀번호는 암호화
        User user = User.builder()
                .userId(dto.getUserId())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
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

        // 응답 DTO 변환 및 반환
        return UserResponseDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .build();
    }

    // 사용자 로그인 처리 및 토큰 발급
    @Transactional(readOnly = true)
    public UserResponseDto login(UserLoginRequestDto dto) {

        // 입력받은 아이디로 사용자 조회
        // 존재하지 않으면 예외 발생
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 일치 여부 검증
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        // 인증 성공 시 JWT 토큰 생성
        String token = jwtTokenProvider.createToken(user.getUserId(), "USER");

        // 토큰 및 사용자 정보를 포함한 응답 DTO 반환
        return UserResponseDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }

    // PUT /api/wc/users/me (사용자 프로필 수정)
    @Transactional
    public UserResponseDto updateProfile(String userId, UserProfileUpdateRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.updateProfile(dto.getNickname(), dto.getProfileImageUrl());

        return UserResponseDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .build();
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
    public void withdraw(String userId, UserWithdrawRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 일반 회원가입 유저인 경우 비밀번호 검증
        if (user.getProvider() == Provider.LOCAL) {
            if (dto.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                throw new CustomException(ErrorCode.INVALID_PASSWORD);
            }
        }

        // 탈퇴 시 DB Refresh Token도 함께 삭제
        refreshTokenRepository.deleteByUserId(userId);
        user.withdraw();
    }

    // POST /api/wc/auth/logout (로그아웃)
    public void logout(String userId, LogoutRequestDto dto) {
        refreshTokenRepository.deleteByUserId(userId);
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
            throw new CustomException(ErrorCode.EXPIRED_TOKEN, "만료된 재설정 토큰입니다. 다시 신청해 주세요.");
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 변경 및 재설정 토큰 사용 완료 처리
        user.updatePassword(passwordEncoder.encode(dto.getNewPassword()));
        resetToken.useToken();
    }

    private String createRandomAuthCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private void sendVerificationEmail(String toEmail, String authCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[SkinCare AI] 회원가입 이메일 인증 번호 안내");
        message.setText("안녕하세요. SkinCare AI 서비스 회원가입 인증 번호는 [" + authCode + "] 입니다.\n\n(5분 이내에 입력해 주세요.)");
        mailSender.send(message);
    }

    private void sendResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[SkinCare AI] 비밀번호 재설정 안내");
        message.setText("안녕하세요. 아래 링크를 클릭하여 비밀번호를 재설정해 주세요.\n\n" + resetLink + "\n\n(해당 링크는 30분간 유효합니다.)");
        mailSender.send(message);
    }
}