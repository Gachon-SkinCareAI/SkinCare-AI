package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import skincare.domain.user.entity.User;

import java.util.Optional;

// 사용자(User) 엔티티에 대한 데이터베이스 접근 인터페이스
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByProviderAndSocialSubjectHash(skincare.domain.user.entity.ENUM.Provider provider, String hash);
    // Legacy lookup; never use email to link social accounts.
    // 이메일로 사용자 조회
    Optional<User> findByEmail(String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :id")
    Optional<User> lockForAnalysisCreation(@org.springframework.data.repository.query.Param("id") String id);

}