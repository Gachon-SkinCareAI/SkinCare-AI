package skincare.domain.user.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "consent_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// ConsentEvent 도메인 상태 관리
public class ConsentEvent {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(nullable = false, length = 40)
    private String policyVersion;

    @Column(nullable = false)
    private boolean granted;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public ConsentEvent(String user, String purpose, String version, boolean granted) {
        this.id = java.util.UUID.randomUUID().toString();
        this.userId = user;
        this.purpose = purpose;
        this.policyVersion = version;
        this.granted = granted;
        this.occurredAt = LocalDateTime.now();
    }
}
