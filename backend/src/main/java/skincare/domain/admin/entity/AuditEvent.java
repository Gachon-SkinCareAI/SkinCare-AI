package skincare.domain.admin.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// AuditEvent 도메인 상태 관리
public class AuditEvent {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String actorId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 36)
    private String targetId;

    @Column(length = 100)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public AuditEvent(String actor, String action, String target, String detail) {
        id = java.util.UUID.randomUUID().toString();
        actorId = actor;
        this.action = action;
        targetId = target;
        this.detail = detail;
        occurredAt = LocalDateTime.now();
    }
}
