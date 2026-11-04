package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.user.entity.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenIdAndUsedFalse(String tokenId);
}