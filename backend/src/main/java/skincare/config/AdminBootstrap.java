package skincare.config;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import skincare.repository.AdminRepository;
import skincare.domain.admin.entity.Admin;
import skincare.service.ServiceInput;
@Component @RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final skincare.service.AdminAuditService audit;
    private final AdminRepository admins; private final PasswordEncoder encoder;
    @Value("${skincare.bootstrap.admin-id:}") private String id;
    @Value("${skincare.bootstrap.admin-password:}") private String password;
    @Override @Transactional public void run(ApplicationArguments args) {
        if(id.isBlank() && password.isBlank()) return;
        if(admins.count()!=0) return; // Never overwrite an existing account.
        ServiceInput.text(id,36,"초기 관리자 ID");ServiceInput.password(password);
        if(!id.matches("[A-Za-z0-9_.-]{3,36}"))throw new IllegalArgumentException("Invalid bootstrap admin ID");
        admins.save(Admin.builder().adminId(id).name("운영 관리자").password(encoder.encode(password)).build());
        audit.record("SYSTEM","ADMIN_BOOTSTRAP",id,null);
    }
}
