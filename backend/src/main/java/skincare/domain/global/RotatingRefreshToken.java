package skincare.domain.global;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [리프레시 토큰] JWT Refresh Token 저장 엔티티
// - 토큰 원문은 저장하지 않고 SHA-256 해시만 저장 (DB 유출 시에도 토큰 재사용 불가)
// - 기기별 로그인을 위해 사용자 1명이 여러 토큰을 가질 수 있음
// - 재발급 시 기존 토큰을 삭제하고 새 토큰을 발급 (Refresh Token Rotation)
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_token_subject", columnList = "subject_id, role"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RotatingRefreshToken extends BaseTimeEntity {

    // Refresh Token SHA-256 해시 (16진수 64자)
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    // 토큰 소유자 ID (사용자 ID 또는 관리자 ID)
    @Column(name = "subject_id", nullable = false, length = 36)
    private String subjectId;

    // 토큰 소유자 권한 (USER, ADMIN)
    @Column(nullable = false, length = 10)
    private String role;

    // 토큰 만료 일시
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 토큰 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
