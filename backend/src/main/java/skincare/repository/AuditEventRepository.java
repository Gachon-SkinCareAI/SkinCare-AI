package skincare.repository;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.*;
import skincare.domain.admin.entity.AuditEvent;
public interface AuditEventRepository extends Repository<AuditEvent,String> {
    AuditEvent save(AuditEvent event);
    Page<AuditEvent> findAll(Pageable page);
}
