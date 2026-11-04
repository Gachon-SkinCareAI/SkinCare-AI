package skincare.domain.admin.entity;

import skincare.domain.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// [관리자 계정] 웹 관리자 대시보드 접근을 위한 단일 관리자 계정 엔티티
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

    // Rotated on logout/password reset; old access tokens stop working immediately.
    @Column(name = "token_version", length = 36)
    private String tokenVersion;

    public String currentTokenVersion() {
        return tokenVersion == null ? "0" : tokenVersion;
    }

    public void revokeTokens() {
        tokenVersion = java.util.UUID.randomUUID().toString();
    }

    // 관리자 비밀번호
    @Column(nullable = false, length = 255)
    private String password;

    // 관리자 이름 (표시용)
    @Column(nullable = false, length = 50)
    private String name;

    // 비밀번호 업데이트 메서드
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
