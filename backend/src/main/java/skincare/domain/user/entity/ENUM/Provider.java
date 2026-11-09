package skincare.domain.user.entity.ENUM;

/** LOCAL is retained only for historical accounts; new user authentication is social-only. */
// Provider 도메인 상태 관리
public enum Provider {
    LOCAL,
    GOOGLE,
    KAKAO,
    NAVER,
    GUEST
}
