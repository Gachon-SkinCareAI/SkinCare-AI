package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.global.RefreshToken;

import java.util.Optional;

// 리프레시 토큰(RefreshToken) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    // 사용자 ID로 Refresh Token 조회
    Optional<RefreshToken> findByUserId(String userId);
    // 사용자 ID로 Refresh Token 삭제 (로그아웃/탈퇴 시)
    void deleteByUserId(String userId);
}
