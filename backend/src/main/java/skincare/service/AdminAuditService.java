package skincare.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import skincare.domain.admin.entity.AuditEvent;
import skincare.repository.AuditEventRepository;
import skincare.dto.ServicePage;
@Service @RequiredArgsConstructor
public class AdminAuditService {
    private final AuditEventRepository events;
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(String actor,String action,String target,String detail) {events.save(new AuditEvent(actor,action,target,detail));}
    @Transactional(propagation=Propagation.MANDATORY)
    public void current(String action,String target,String detail) {
        var principal=SecurityContextHolder.getContext().getAuthentication();
        if(principal==null||principal.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new IllegalStateException("Admin audit actor required");
        record(principal.getName(),action,target,detail);
    }
    @Transactional(readOnly=true) public ServicePage<AuditEvent> list(int page,int size) {
        return ServicePage.from(events.findAll(ServiceInput.page(page,size,"occurredAt","id")));
    }
}
