package skincare.domain.user.entity.ENUM;

// 사용자 계정 상태 (ACTIVE: 활성, INACTIVE: 휴면, SUSPENDED: 정지, DELETED: 탈퇴)
// SUSPENDED/DELETED 계정은 로그인 및 기존 토큰 사용이 차단됨
public enum UserStatus {
    ACTIVE, INACTIVE, SUSPENDED, DELETED
}
