package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import skincare.domain.user.entity.UserSettings;

// UserSettingsRepository 데이터 접근 처리
public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {}
