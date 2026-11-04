package skincare.repository;
import skincare.domain.hospital.entity.Hospital;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface HospitalRepository extends JpaRepository<Hospital,String>,JpaSpecificationExecutor<Hospital> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hospital h where h.hospitalId=:id")
    Optional<Hospital> lockById(@Param("id") String id);
    long countByStatus(String status);
}
