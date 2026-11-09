package skincare.security.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserSettings;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;
import skincare.repository.UserRepository;
import skincare.security.AccountStatusChecker;

// 구글 계정 정보로 기존 회원 조회 또는 신규 회원 등록 컴포넌트
// 웹 OAuth2/OIDC 로그인(CustomOidcUserService, CustomOAuth2UserService)과
// 모바일 id_token 로그인(POST /api/wc/auth/social/google)에서 공통으로 사용
@Slf4j
@Component
@RequiredArgsConstructor
// GoogleUserRegistrar 인증과 권한 처리
public class GoogleUserRegistrar {

    private final UserRepository userRepository; // 사용자 데이터 베이스 접근용 Repository

    // 이메일 기준 기존 회원 조회, 없으면 구글 회원으로 신규 등록 (탈퇴/정지 계정은 USER-004/USER-003 예외)
    @Transactional
    public User findOrRegister(String email, String name, String picture, String sub) {
        if (email == null || email.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "구글 계정의 이메일 정보를 확인할 수 없습니다.");
        }

        User user =
                userRepository
                        .findByEmail(email)
                        .map(
                                existingUser -> {
                                    // 사용자가 직접 변경한 닉네임/프로필 사진을 덮어쓰지 않도록 비어 있는 항목만 구글 정보로 채움
                                    existingUser.updateProfile(
                                            existingUser.getNickname() == null ? name : null,
                                            existingUser.getProfileImageUrl() == null
                                                    ? picture
                                                    : null);
                                    return existingUser;
                                })
                        .orElseGet(() -> registerGoogleUser(email, name, picture, sub));

        // 탈퇴/정지 계정은 소셜 로그인 차단 (예외 발생 시 프로필 갱신도 롤백)
        ErrorCode blockedReason = AccountStatusChecker.resolveBlockedReason(user.getStatus());
        if (blockedReason != null) {
            throw new CustomException(blockedReason);
        }

        user.updateLastAccess();
        return user;
    }

    // 구글 소셜 로그인 신규 사용자 등록 및 기본 환경설정 생성
    private User registerGoogleUser(String email, String name, String picture, String sub) {
        // 소셜 로그인 사용자의 고유 userId 생성 (예: google_123456789)
        User user =
                User.builder()
                        .userId("google_" + sub)
                        .email(email)
                        .nickname(name != null ? name : "GoogleUser")
                        .profileImageUrl(picture)
                        .provider(Provider.GOOGLE)
                        .agreedTerms(true)
                        .agreedPrivacy(true)
                        .build();

        // 기본 환경설정 자동 생성 (User 저장 시 cascade로 함께 저장)
        user.initSettings(UserSettings.builder().user(user).build());

        User saved = userRepository.saveAndFlush(user);
        log.info("[Auth] 정상 처리, Google 소셜 회원가입 완료 (user_id: {})", saved.getUserId());
        return saved;
    }
}
