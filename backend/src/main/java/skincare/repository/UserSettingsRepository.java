package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.user.entity.UserSettings;

public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {
}