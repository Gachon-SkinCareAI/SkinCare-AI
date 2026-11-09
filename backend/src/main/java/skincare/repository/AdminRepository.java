package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import skincare.domain.admin.entity.Admin;

// 관리자(Admin) 엔티티에 대한 데이터베이스 접근 인터페이스
@Repository
// AdminRepository 데이터 접근 처리
public interface AdminRepository extends JpaRepository<Admin, String> {}
