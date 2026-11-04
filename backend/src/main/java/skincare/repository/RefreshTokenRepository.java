package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.global.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByUserId(String userId);
    void deleteByUserId(String userId);
}
