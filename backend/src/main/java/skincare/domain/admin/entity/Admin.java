package skincare.domain.admin.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [관리자 계정] 웹 관리자 대시보드 접근을 위한 관리자 계정 엔티티
@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Admin extends BaseTimeEntity {

    // 관리자 고유 ID
    @Id
    @Column(name = "admin_id", length = 36)
    private String adminId;

    // 관리자 이메일 (로그인 ID)
    // 이메일 컬럼 추가 이전에 생성된 관리자 계정 호환을 위해 DB 제약은 nullable로 두고, 신규 계정은 필수 입력
    @Column(unique = true, length = 100)
    private String email;

    // 관리자 비밀번호
    @Column(nullable = false, length = 255)
    private String password;

    // 관리자 이름 (표시용)
    @Column(nullable = false, length = 50)
    private String name;

    // 관리자 권한 등급 (ROLE_SUPER_ADMIN: 최고 관리자, ROLE_ADMIN: 일반 관리자)
    @Column(length = 30)
    @Builder.Default
    private String role = "ROLE_ADMIN";

    // 소속 부서 (ex. 운영관리팀)
    @Column(length = 50)
    private String department;

    // 최종 로그인 일시
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // 비밀번호 업데이트 메서드
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 최종 로그인 일시 갱신 메서드
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
