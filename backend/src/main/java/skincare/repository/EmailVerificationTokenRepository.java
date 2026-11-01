package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.user.entity.EmailVerificationToken;

import java.util.Optional;

// 이메일 인증 토큰(EmailVerificationToken) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {
    // 인증이 완료된 이메일 인증 토큰 조회
    Optional<EmailVerificationToken> findByEmailAndVerifiedTrue(String email);
}