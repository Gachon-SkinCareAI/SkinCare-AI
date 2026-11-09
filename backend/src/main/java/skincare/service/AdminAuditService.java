package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import skincare.domain.admin.entity.AuditEvent;
import skincare.dto.ServicePage;
import skincare.repository.AuditEventRepository;

@Service
@RequiredArgsConstructor
// AdminAuditService 비즈니스 로직 처리
public class AdminAuditService {

    private final AuditEventRepository events;

    // MANDATORY 대신 기본 REQUIRED(또는 비즈니스 롤백과 분리하려면 REQUIRES_NEW) 적용
    @Transactional
    public void record(String actor, String action, String target, String detail) {
        events.save(new AuditEvent(actor, action, target, detail));
    }

    @Transactional
    public void recordWithCurrentAdmin(String action, String target, String detail) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 1. 로그인 인증 정보가 없는 경우 (회원가입, 미인증 요청 등)
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            record("SYSTEM", action, target, detail);
            return;
        }

        // 2. ROLE_ADMIN 권한 보유 여부 확인
        boolean isAdmin =
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // 관리자가 맞으면 해당 관리자 ID로, 일반 사용자나 기타 권한이면 주체 이름 또는 SYSTEM으로 처리
        String actor = isAdmin ? auth.getName() : "SYSTEM";

        record(actor, action, target, detail);
    }

    // 기존 signUp 등에서 AdminAuditService.current(...) 형태로 호출하던 부분을 위한 하위 호환 메서드
    @Transactional
    public void current(String action, String target, String detail) {
        recordWithCurrentAdmin(action, target, detail);
    }

    @Transactional(readOnly = true)
    public ServicePage<AuditEvent> list(int page, int size) {
        return ServicePage.from(events.findAll(ServiceInput.page(page, size, "occurredAt", "id")));
    }
}
