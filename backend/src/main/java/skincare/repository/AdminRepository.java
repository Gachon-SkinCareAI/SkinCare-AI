package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import skincare.domain.admin.entity.Admin;

import java.util.Optional;

// 관리자(Admin) 엔티티에 대한 데이터베이스 접근 인터페이스
@Repository
public interface AdminRepository extends JpaRepository<Admin, String> {

    // 이메일로 관리자 조회 (관리자 로그인)
    Optional<Admin> findByEmail(String email);

    // 관리자 이메일 중복 여부 확인
    boolean existsByEmail(String email);
}
