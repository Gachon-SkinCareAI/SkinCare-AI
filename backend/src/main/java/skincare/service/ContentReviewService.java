package skincare.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.ClassPathResource;
import static org.springframework.http.HttpStatus.*;
import skincare.domain.admin.entity.ContentReview;
import skincare.repository.ContentReviewRepository;
import skincare.dto.ServicePage;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class ContentReviewService {
    private final ContentReviewRepository reviews;private final AdminAuditService audit;
    private final jakarta.persistence.EntityManager em;private final Set<String> clinicians;
    static final Set<String> KINDS=Set.of("CARE_GUIDE","QUESTION_BANK","RISK_RULE","POLICY");
    static final Set<String> STATES=Set.of("DRAFT","SUBMITTED","APPROVED","REJECTED");
    public ContentReviewService(ContentReviewRepository reviews,AdminAuditService audit,jakarta.persistence.EntityManager em,
        @Value("${skincare.review.clinical-reviewers:}")String reviewers) {
        this.reviews=reviews;this.audit=audit;this.em=em;
        clinicians=new HashSet<>(Arrays.stream(reviewers.split(",")).map(String::strip).toList());clinicians.removeIf(String::isBlank);
    }
    public record Draft(String clientRequestId,String kind,String title,String content,String sourceUrl,String supersedesId) {}
    public record Transition(Long expectedVersion,String action,String reason,String evidenceUrl) {}
    @Transactional public ContentReview create(String actor,Draft d) {
        if(!KINDS.contains(Objects.toString(d.kind(),"")))throw new ResponseStatusException(BAD_REQUEST,"검수 종류를 선택해 주세요.");
        String id=ServiceInput.uuid(d.clientRequestId()),title=ServiceInput.text(d.title(),200,"제목");
        String content=d.content();
        if(content==null||content.isBlank()||content.length()>50000)throw new ResponseStatusException(BAD_REQUEST,"검수 원문은 1~50000자여야 합니다.");
        String source=ServiceInput.https(d.sourceUrl(),1000);
        String previous=ServiceInput.optional(d.supersedesId(),36,"이전 검수 번호");
        if(previous!=null && !reviews.findById(previous).map(r->r.getKind().equals(d.kind())).orElse(false))
            throw new ResponseStatusException(BAD_REQUEST,"같은 종류의 이전 검수를 지정해 주세요.");
        var old=reviews.findById(id);
        if(old.isPresent()) {
            var r=old.get();if(r.getAuthorId().equals(actor)&&r.getKind().equals(d.kind())&&r.getTitle().equals(title)&&r.getContent().equals(content)&&r.getSourceUrl().equals(source)&&Objects.equals(previous,r.getSupersedesId()))return r;
            throw conflict();
        }
        var r=new ContentReview(id,d.kind(),title,content,hash(content),source,previous,actor);
        em.persist(r);em.flush();audit.record(actor,"REVIEW_CREATE",id,r.getContentHash());return r;
    }
    @Transactional public ContentReview transition(String actor,String id,Transition d) {
        var r=reviews.lockById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND));
        if(d.expectedVersion()==null||d.expectedVersion()!=r.getVersion())throw conflict();
        switch(Objects.toString(d.action(),"")) {
            case "SUBMIT" -> {
                if(!r.getStatus().equals("DRAFT")||!r.getAuthorId().equals(actor))throw conflict();r.submit();
            }
            case "APPROVE","REJECT" -> {
                if(!r.getStatus().equals("SUBMITTED"))throw conflict();
                if(r.getAuthorId().equals(actor))throw new ResponseStatusException(FORBIDDEN,"작성자는 자신의 자료를 승인하거나 반려할 수 없습니다.");
                if(d.action().equals("APPROVE")&&!r.getKind().equals("POLICY")&&!clinicians.contains(actor))
                    throw new ResponseStatusException(FORBIDDEN,"지정된 임상 검수 담당자만 승인할 수 있습니다.");
                String reason=ServiceInput.text(d.reason(),2000,"검수 근거");
                String evidence=d.action().equals("APPROVE")?ServiceInput.https(d.evidenceUrl(),1000):ServiceInput.optional(d.evidenceUrl(),1000,"근거 URL");
                if(evidence!=null)evidence=ServiceInput.https(evidence,1000);
                r.decide(d.action().equals("APPROVE")?"APPROVED":"REJECTED",actor,reason,evidence);
            }
            default -> throw new ResponseStatusException(BAD_REQUEST,"검수 작업을 확인해 주세요.");
        }
        reviews.flush();audit.record(actor,"REVIEW_"+d.action(),id,r.getContentHash());return r;
    }
    @Transactional(readOnly=true) public ServicePage<Map<String,Object>> list(String status,int page,int size) {
        var p=ServiceInput.page(page,size,"createdAt","id");
        if(status!=null&&!STATES.contains(status))throw new ResponseStatusException(BAD_REQUEST);
        var results=status==null?reviews.findAll(p):reviews.findByStatus(status,p);
        return ServicePage.from(results.map(r->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getId());m.put("kind",r.getKind());m.put("title",r.getTitle());m.put("status",r.getStatus());m.put("contentHash",r.getContentHash());m.put("authorId",r.getAuthorId());m.put("createdAt",r.getCreatedAt());return m;}));
    }
    @Transactional(readOnly=true) public ContentReview get(String id){return reviews.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND));}
    public Map<String,Object> capabilities(String actor){return Map.of("actorId",actor,"clinicalReviewer",clinicians.contains(actor),"publishEnabled",false);}
    public Map<String,String> catalog(String kind) {
        String file=switch(kind){case "CARE_GUIDE"->"cards.yaml";case "QUESTION_BANK"->"bank.yaml";case "RISK_RULE"->"rules.yaml";default->throw new ResponseStatusException(BAD_REQUEST);};
        try(var input=new ClassPathResource("review-catalog/"+file).getInputStream()) {
            String content=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            return Map.of("kind",kind,"content",content,"contentHash",hash(content),"origin","현재 서버 빌드의 "+file);
        }catch(java.io.IOException e){throw new ResponseStatusException(SERVICE_UNAVAILABLE,"검수용 원문을 불러오지 못했습니다.");}
    }
    static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private ResponseStatusException conflict(){return new ResponseStatusException(CONFLICT,"검수 상태 또는 내용이 바뀌었습니다. 다시 조회해 주세요.");}
}
