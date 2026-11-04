package skincare.domain.admin.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="content_reviews") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ContentReview {
    @Id @Column(length=36) private String id;
    @Version private long version;
    @Column(nullable=false,length=30) private String kind;
    @Column(nullable=false,length=200) private String title;
    @Column(nullable=false,columnDefinition="MEDIUMTEXT") private String content;
    @Column(nullable=false,length=64) private String contentHash;
    @Column(nullable=false,length=1000) private String sourceUrl;
    @Column(length=36) private String supersedesId;
    @Column(nullable=false,length=36) private String authorId;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false) private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    @Column(length=36) private String reviewerId;
    private LocalDateTime decidedAt;
    @Column(length=2000) private String reason;
    @Column(length=1000) private String evidenceUrl;
    public ContentReview(String id,String kind,String title,String content,String hash,String source,String previous,String author) {
        this.id=id;this.kind=kind;this.title=title;this.content=content;contentHash=hash;sourceUrl=source;
        supersedesId=previous;authorId=author;status="DRAFT";createdAt=LocalDateTime.now();
    }
    public void submit(){status="SUBMITTED";submittedAt=LocalDateTime.now();}
    public void decide(String state,String reviewer,String reason,String evidence) {
        status=state;reviewerId=reviewer;this.reason=reason;evidenceUrl=evidence;decidedAt=LocalDateTime.now();
    }
}
