package skincare.domain.analysis;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;

import skincare.domain.global.BaseTimeEntity;

import java.util.UUID;

@Entity
@Table(
        name = "analysis_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "client_request_id"}),
        indexes = @Index(name = "ix_analysis_owner_created", columnList = "user_id,created_at"))
@Getter
@NoArgsConstructor
// AnalysisSession 도메인 상태 관리
public class AnalysisSession extends BaseTimeEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "client_request_id", nullable = false, length = 36)
    private String clientRequestId;

    @Column(name = "case_id", nullable = false, length = 36)
    private String caseId;

    @Column(nullable = false, length = 30)
    private String status;

    @Version private long version;

    @Column(nullable = false)
    private int turn;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] image;

    @Column(nullable = false, length = 30)
    private String imageType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String slotsJson = "{}";

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String visionJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String questionJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(length = 50)
    private String errorCode;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "progress_note", length = 2000)
    private String progressNote;

    @Column(name = "captured_on")
    private java.time.LocalDate capturedOn;

    @Column(length = 16)
    private String swelling;

    @Column(length = 16)
    private String exudate;

    public void recordCapture(java.time.LocalDate date) {
        capturedOn = date;
    }

    public void recordSymptoms(String swelling, String exudate) {
        this.swelling = swelling;
        this.exudate = exudate;
    }

    @Column(name = "source_kind", nullable = false, length = 20)
    private String sourceKind = "MEMBER";

    @Column(name = "training_eligible", nullable = false)
    private boolean trainingEligible;

    @Column(name = "training_consent_version", length = 40)
    private String trainingConsentVersion;

    @Column(name = "training_consented_at")
    private java.time.LocalDateTime trainingConsentedAt;

    @Column(name = "training_review_status", nullable = false, length = 20)
    private String trainingReviewStatus = "NOT_ELIGIBLE";

    public void snapshotDataUse(skincare.domain.user.entity.User owner) {
        sourceKind =
                owner.getProvider() == skincare.domain.user.entity.ENUM.Provider.GUEST
                        ? "GUEST"
                        : "MEMBER";
        trainingEligible =
                owner.isTrainingGranted()
                        && skincare.service.ConsentPolicy.TRAINING_VERSION.equals(
                                owner.getTrainingConsentVersion());
        if (trainingEligible) {
            trainingConsentVersion = owner.getTrainingConsentVersion();
            trainingConsentedAt = owner.getTrainingConsentedAt();
            trainingReviewStatus = "PENDING";
        }
    }

    public void recordProgress(Integer pain, String note) {
        painScore = pain;
        progressNote = note;
    }

    public AnalysisSession(
            String userId, String requestId, String caseId, byte[] image, String imageType) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.clientRequestId = requestId;
        this.caseId = caseId == null ? id : caseId;
        this.image = image;
        this.imageType = imageType;
        this.status = "CREATED";
    }

    public void apply(String slots, String vision, String question, String result, int turn) {
        slotsJson = slots;
        visionJson = vision;
        questionJson = question;
        resultJson = result;
        this.turn = turn;
        status = result == null ? "QUESTIONNAIRE" : "COMPLETED";
        errorCode = null;
    }

    public void fail(String code) {
        status = "RETRY_REQUIRED";
        errorCode = code;
    }
}
