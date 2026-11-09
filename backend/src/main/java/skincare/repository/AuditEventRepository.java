package skincare.repository;

import org.springframework.data.domain.*;
import org.springframework.data.repository.Repository;

import skincare.domain.admin.entity.AuditEvent;

// AuditEventRepository 데이터 접근 처리
public interface AuditEventRepository extends Repository<AuditEvent, String> {
    AuditEvent save(AuditEvent event);

    Page<AuditEvent> findAll(Pageable page);
}
