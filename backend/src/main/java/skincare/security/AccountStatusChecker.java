package skincare.security;

import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.exception.ErrorCode;

// 사용자 계정 상태에 따른 서비스 이용 차단 여부 판별 유틸리티
// 일반 로그인(UserService), 소셜 로그인(OAuth/OIDC), JWT 인증 필터에서 동일한 기준으로 사용
public final class AccountStatusChecker {

    private AccountStatusChecker() {
    }

    // 이용이 차단된 계정이면 차단 사유 ErrorCode, 이용 가능한 계정이면 null 반환
    public static ErrorCode resolveBlockedReason(UserStatus status) {
        if (status == UserStatus.DELETED) {
            return ErrorCode.USER_WITHDRAWN;
        }
        if (status == UserStatus.SUSPENDED) {
            return ErrorCode.USER_SUSPENDED;
        }
        return null;
    }
}
