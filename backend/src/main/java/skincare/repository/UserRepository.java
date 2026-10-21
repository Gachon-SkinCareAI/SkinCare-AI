package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import skincare.domain.user.entity.User;

import java.util.Optional;

// 사용자(User) 엔티티에 대한 데이터베이스 접근 인터페이스
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    // 이메일로 사용자 조회
    Optional<User> findByEmail(String email);
}