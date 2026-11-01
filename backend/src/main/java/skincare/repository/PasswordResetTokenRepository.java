package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.user.entity.PasswordResetToken;

import java.util.Optional;

// 비밀번호 재설정 토큰(PasswordResetToken) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    // 아직 사용되지 않은 재설정 토큰 조회
    Optional<PasswordResetToken> findByTokenIdAndUsedFalse(String tokenId);
}