package skincare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import skincare.domain.global.RotatingRefreshToken;

import java.time.LocalDateTime;

// 리프레시 토큰(RotatingRefreshToken) 엔티티에 대한 데이터베이스 접근 인터페이스 (기본키: 토큰 해시)
public interface RotatingRefreshTokenRepository extends JpaRepository<RotatingRefreshToken, String> {

    // 특정 사용자/관리자의 모든 Refresh Token 삭제 (전체 로그아웃, 탈퇴, 계정 정지 시)
    // clearAutomatically를 사용하면 같은 트랜잭션에서 변경한 회원 상태(정지/탈퇴)가 저장 전에 버려지므로 사용하지 않고,
    //   벌크 삭제 전에 대기 중인 변경 사항을 먼저 반영(flushAutomatically)
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RotatingRefreshToken r WHERE r.subjectId = :subjectId AND r.role = :role")
    int deleteAllBySubject(@Param("subjectId") String subjectId, @Param("role") String role);

    // 만료된 Refresh Token 일괄 삭제 (토큰 발급 시 함께 정리)
    @Modifying
    @Query("DELETE FROM RotatingRefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
