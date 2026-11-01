package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import skincare.domain.user.entity.UserSettings;

// 사용자 환경설정(UserSettings) 엔티티에 대한 데이터베이스 접근 인터페이스
public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {
}