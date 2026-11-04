package skincare.domain.user.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
/** Minimal restore-suppression ledger; no email, subject, images or clinical data. */
@Entity @Table(name="account_erasure_receipts") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AccountErasureReceipt {
    @Id @Column(length=36) private String id;
    @Column(nullable=false,length=36,unique=true) private String userId;
    @Column(nullable=false) private LocalDateTime requestedAt;
}
