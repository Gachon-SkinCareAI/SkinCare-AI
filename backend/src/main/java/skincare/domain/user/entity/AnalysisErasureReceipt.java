package skincare.domain.user.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

/** Restore suppression only; contains no photo, answer or account profile. */
@Entity
@Table(name = "analysis_erasure_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// AnalysisErasureReceipt 도메인 상태 관리
public class AnalysisErasureReceipt {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36, unique = true)
    private String sessionId;

    @Column(nullable = false)
    private LocalDateTime requestedAt;
}
