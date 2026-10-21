package skincare.domain.user.entity;

import skincare.domain.global.BaseTimeEntity;
import skincare.domain.user.entity.ENUM.PlanType;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.ENUM.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 사용자 엔티티
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {
    // 사용자 고유 아이디
    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    // 로그인 이메일
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 비밀번호
    @Column(length = 255)
    private String password;

    // 닉네임
    @Column(nullable = false, length = 50)
    private String nickname;

    // 프로필 이미지 url
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // 가입경로(LOCAL: 일반, GOOGLE: 구글 소셜)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Provider provider = Provider.LOCAL;

    // 이용 플랜 (FREE, PREMIUM 등)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlanType plan = PlanType.FREE;

    // 계정 상태 (ACTIVE: 활성, INACTIVE: 휴면, DELETED: 탈퇴)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // 서비스 이용약관 동의 여부
    @Column(name = "agreed_terms", nullable = false)
    private boolean agreedTerms;

    // 개인정보 처리방침 동의 여부
    @Column(name = "agreed_privacy", nullable = false)
    private boolean agreedPrivacy;

    // 최종 서비스 접속 일시
    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    public void initSettings(UserSettings settings) {
        this.settings = settings;
    }

    // 사용자 서비스 환경설정
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserSettings settings;

    // 프로필 수정 메서드
    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null) this.nickname = nickname;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
    }

    // 회원 탈퇴 처리 메서드
    public void withdraw() {
        this.status = UserStatus.DELETED;
    }

    // 최종 접속 시간 갱신 메서드
    public void updateLastAccess() {
        this.lastAccessAt = LocalDateTime.now();
    }

    // 비밀번호 업데이트 메서드
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
