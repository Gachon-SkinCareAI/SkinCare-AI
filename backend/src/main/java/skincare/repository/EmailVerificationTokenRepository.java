package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import skincare.domain.user.entity.EmailVerificationToken;

import java.util.Optional;

// EmailVerificationTokenRepository 데이터 접근 처리
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, String> {
    Optional<EmailVerificationToken> findByEmailAndVerifiedTrue(String email);
}
