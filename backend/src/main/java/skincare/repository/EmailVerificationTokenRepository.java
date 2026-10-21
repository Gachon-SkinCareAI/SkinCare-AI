package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.user.entity.EmailVerificationToken;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {
    Optional<EmailVerificationToken> findByEmailAndVerifiedTrue(String email);
}