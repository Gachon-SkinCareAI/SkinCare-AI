package skincare.domain.user.entity;

import jakarta.persistence.*;

import lombok.*;

import skincare.domain.global.BaseTimeEntity;
import skincare.domain.user.entity.ENUM.PlanType;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.ENUM.UserStatus;

import java.time.LocalDateTime;

// 사용자 엔티티
@Entity
@Table(
        name = "users",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_users_social_identity",
                        columnNames = {"provider", "social_subject_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
// User 도메인 상태 관리
public class User extends BaseTimeEntity {
    // 사용자 고유 아이디
    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    // 로그인 이메일
    @Column(length = 254)
    private String email;

    // Rotated on logout/password reset; old access tokens stop working immediately.
    @Column(name = "token_version", length = 36)
    private String tokenVersion;

    public String currentTokenVersion() {
        return tokenVersion == null ? "0" : tokenVersion;
    }

    public void revokeTokens() {
        tokenVersion = java.util.UUID.randomUUID().toString();
    }

    // Hash preserves case-sensitive provider subjects even under MySQL's default collation.
    @Column(name = "social_subject_hash", length = 64)
    private String socialSubjectHash;

    public void attachSocialSubject(String hash) {
        this.socialSubjectHash = hash;
    }

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

    @Column(name = "terms_version", length = 40)
    private String termsVersion;

    @Column(name = "privacy_version", length = 40)
    private String privacyVersion;

    @Column(name = "consented_at")
    private LocalDateTime consentedAt;

    public void acceptConsents(String terms, String privacy) {
        agreedTerms = true;
        agreedPrivacy = true;
        termsVersion = terms;
        privacyVersion = privacy;
        consentedAt = LocalDateTime.now();
    }

    public boolean hasRequiredConsents() {
        return agreedTerms
                && agreedPrivacy
                && consentedAt != null
                && healthConsentedAt != null
                && skincare.service.ConsentPolicy.HEALTH_VERSION.equals(healthConsentVersion)
                && skincare.service.ConsentPolicy.VERSION.equals(termsVersion)
                && skincare.service.ConsentPolicy.VERSION.equals(privacyVersion);
    }

    @Column(name = "health_consent_version", length = 40)
    private String healthConsentVersion;

    @Column(name = "health_consented_at")
    private LocalDateTime healthConsentedAt;

    @Column(name = "training_consent_version", length = 40)
    private String trainingConsentVersion;

    @Column(name = "training_consented_at")
    private LocalDateTime trainingConsentedAt;

    @Column(name = "training_granted", nullable = false)
    private boolean trainingGranted;

    public void acceptHealthConsent(String version) {
        healthConsentVersion = version;
        healthConsentedAt = LocalDateTime.now();
    }

    public void trainingConsent(boolean granted, String version) {
        trainingGranted = granted;
        trainingConsentVersion = version;
        trainingConsentedAt = LocalDateTime.now();
    }

    // A device capability owns guest records; no email/social identity is fabricated.
    @Column(name = "guest_key_hash", length = 64, unique = true)
    private String guestKeyHash;

    @Column(name = "guest_policy_version", length = 40)
    private String guestPolicyVersion;

    @Column(name = "location_granted", nullable = false)
    private boolean locationGranted;

    @Column(name = "location_consent_version", length = 40)
    private String locationConsentVersion;

    @Column(name = "location_consented_at")
    private LocalDateTime locationConsentedAt;

    public void acceptGuest(String hash, String version) {
        guestKeyHash = hash;
        guestPolicyVersion = version;
        consentedAt = LocalDateTime.now();
    }

    public void locationConsent(boolean granted, String version) {
        locationGranted = granted;
        locationConsentVersion = version;
        locationConsentedAt = LocalDateTime.now();
    }

    public boolean hasLocationConsent() {
        return locationGranted
                && skincare.service.ConsentPolicy.LOCATION_VERSION.equals(locationConsentVersion);
    }

    public boolean activeGuest() {
        return provider == Provider.GUEST
                && status == UserStatus.ACTIVE
                && guestKeyHash != null
                && skincare.service.ConsentPolicy.GUEST_VERSION.equals(guestPolicyVersion)
                && consentedAt != null
                && getCreatedAt() != null
                && getCreatedAt().plusYears(5).isAfter(LocalDateTime.now());
    }

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

    // 사용자 비밀번호 변경
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changePlan(PlanType plan) {
        if (plan != null) this.plan = plan;
    }

    public void changeStatus(UserStatus status) {
        if (status != null) {
            this.status = status;
            revokeTokens();
        }
    }
}
