package backend.skincare;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import skincare.domain.user.entity.User;
import skincare.domain.admin.entity.Admin;
import skincare.domain.analysis.*;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:completion;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "skincare.consent.training-policy-url=https://example.test/training", "skincare.review.clinical-reviewers=review-expert"})
class ServiceCompletionTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;@Autowired UserRepository users;@Autowired AdminRepository admins;
    @Autowired AnalysisSessionRepository sessions;@Autowired AnalysisAnswerRepository answers;
    @Autowired JwtTokenProvider jwt;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    MockMvc mvc;String id,owner,other,admin,expert;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();id=UUID.randomUUID().toString();owner=account(id,true);other=account(UUID.randomUUID().toString(),true);admin=administrator("review-author");expert=administrator("review-expert");
    }
    String account(String id,boolean consent){var u=User.builder().userId(id).provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).socialSubjectHash(UUID.randomUUID().toString()).nickname("QA 사용자").email(id+"@example.test").build();if(consent){u.acceptConsents("2026-11-03","2026-11-03");u.acceptHealthConsent("2026-11-03");}users.saveAndFlush(u);return "Bearer "+jwt.createToken(id,"USER",u.currentTokenVersion());}
    String administrator(String id){var a=admins.findById(id).orElseGet(()->admins.saveAndFlush(Admin.builder().adminId(id).name("QA 관리자").password("unused").build()));return "Bearer "+jwt.createToken(id,"ADMIN",a.currentTokenVersion());}
    Map<String,Object> body(MvcResult r)throws Exception{var s=r.getResponse().getContentAsString();return s.isBlank()?Map.of():json.readValue(s,Map.class);}
    Map<String,Object> put(String token,String path,Object data,int status)throws Exception{return body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().is(status)).andReturn());}
    Map<String,Object> post(String token,String path,Object data,int status)throws Exception{return body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().is(status)).andReturn());}
    Map<String,Object> getData(String token,String path)throws Exception{return body(mvc.perform(get(path).header("Authorization",token)).andExpect(status().isOk()).andReturn());}
    long count(String table,String column,String value){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Long.class,value);}
    Map<String,Object> consent(){return new HashMap<>(Map.of("agreedTerms",true,"agreedPrivacy",true,"termsVersion","2026-11-03","privacyVersion","2026-11-03","agreedHealthData",true,"healthDataVersion","2026-11-03"));}
    @Test void oldConsentsNeedSeparateHealthActionAndHistoryIsIdempotent()throws Exception{
        String newId=UUID.randomUUID().toString(),token=account(newId,false);var u=users.findById(newId).orElseThrow();u.acceptConsents("2026-11-03","2026-11-03");users.saveAndFlush(u);
        mvc.perform(get("/api/wc/analyses").header("Authorization",token)).andExpect(status().isForbidden());
        var d=consent();d.remove("agreedHealthData");put(token,"/api/wc/users/me/consents",d,400);d=consent();d.put("healthDataVersion","old");put(token,"/api/wc/users/me/consents",d,400);
        put(token,"/api/wc/users/me/consents",consent(),200);put(token,"/api/wc/users/me/consents",consent(),200);
        assertEquals(1,count("consent_events","user_id",newId));assertTrue(users.findById(newId).orElseThrow().hasRequiredConsents());
        mvc.perform(get("/api/wc/analyses").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void optionalTrainingCanBeRevokedWithoutLosingServiceAndHistoryIsOwnerOnly()throws Exception{
        assertEquals(false,getData(owner,"/api/wc/users/me/consents").get("trainingGranted"));
        put(owner,"/api/wc/users/me/consents/training",Map.of("granted",true,"version","old"),400);
        put(owner,"/api/wc/users/me/consents/training",Map.of("granted",true,"version","2026-11-01"),204);
        put(owner,"/api/wc/users/me/consents/training",Map.of("granted",false,"version","2026-11-01"),204);
        put(owner,"/api/wc/users/me/consents/training",Map.of("granted",false,"version","2026-11-01"),204);
        var state=getData(owner,"/api/wc/users/me/consents");assertEquals(false,state.get("trainingGranted"));assertEquals(false,state.get("trainingExportEnabled"));assertEquals(2,count("consent_events","user_id",id));
        assertEquals(0,((Map<?,?>)getData(other,"/api/wc/users/me/consents").get("history")).get("total"));
        mvc.perform(get("/api/wc/users/me").header("Authorization",owner)).andExpect(status().isOk());
    }
    String inquiry()throws Exception{return (String)post(owner,"/api/wc/inquiries",Map.of("clientRequestId",UUID.randomUUID().toString(),"type","OTHER","title","QA 문의","content","QA 민감 본문"),201).get("id");}
    void withdraw(String token,Object d,int status)throws Exception{mvc.perform(delete("/api/wc/users/me").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(d))).andExpect(status().is(status));}
    @Test void withdrawalErasesPhotosAnswersInquiriesConsentsAndOldTokenButPreservesOtherAccounts()throws Exception{
        var s=sessions.saveAndFlush(new AnalysisSession(id,UUID.randomUUID().toString(),null,new byte[]{1,2,3},"image/png"));
        answers.saveAndFlush(new AnalysisAnswer(s.getId(),UUID.randomUUID().toString(),"q","a","private",1,"question","answer"));
        String inquiry=inquiry();var i=getData(admin,"/api/wc/admin/inquiries/"+inquiry);
        put(admin,"/api/wc/admin/inquiries/"+inquiry+"/answer",Map.of("clientRequestId",UUID.randomUUID().toString(),"expectedVersion",i.get("version"),"content","QA 답변"),200);
        put(owner,"/api/wc/users/me/consents/training",Map.of("granted",false,"version","2026-11-01"),204);
        withdraw(owner,Map.of(),400);assertTrue(users.existsById(id));withdraw(owner,Map.of("confirmation","탈퇴"),200);
        assertFalse(users.existsById(id));assertFalse(sessions.existsById(s.getId()));assertEquals(0,count("analysis_answers","session_id",s.getId()));
        for(String table:List.of("inquiries","consent_events","user_settings","user_refresh_tokens"))assertEquals(0,count(table,"user_id",id));
        assertEquals(0,count("inquiry_answers","inquiry_id",inquiry));assertEquals(1,count("account_erasure_receipts","user_id",id));
        mvc.perform(get("/api/wc/analyses/"+s.getId()+"/image").header("Authorization",owner)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wc/users/me").header("Authorization",other)).andExpect(status().isOk());
    }
    @Test void erasureRollsBackCompletelyIfAnUnexpectedReferencePreventsDeletion()throws Exception {
        var session=sessions.saveAndFlush(new AnalysisSession(id,UUID.randomUUID().toString(),null,new byte[]{7},"image/png"));
        String table="qa_erasure_block_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE TABLE "+table+" (user_id VARCHAR(36) REFERENCES users(user_id))");jdbc.update("INSERT INTO "+table+" VALUES (?)",id);
        try{withdraw(owner,Map.of("confirmation","탈퇴"),409);assertTrue(users.existsById(id));assertTrue(sessions.existsById(session.getId()));assertEquals(0,count("account_erasure_receipts","user_id",id));}
        finally{jdbc.execute("DROP TABLE "+table);}
    }
    @Test void onboardingAccountCanWithdrawWithoutAcceptingPolicies()throws Exception{String newId=UUID.randomUUID().toString();withdraw(account(newId,false),Map.of("confirmation","탈퇴"),200);assertFalse(users.existsById(newId));}
    @Test void concurrentProfileWriteCannotResurrectWithdrawnAccount()throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var erase=pool.submit(()->{start.await();withdraw(owner,Map.of("confirmation","탈퇴"),200);return true;});
            var update=pool.submit(()->{start.await();return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/wc/users/me").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"경합\"}")).andReturn().getResponse().getStatus();});
            start.countDown();assertTrue(erase.get(20,TimeUnit.SECONDS));assertTrue(Set.of(200,401,404).contains(update.get(20,TimeUnit.SECONDS)));assertFalse(users.existsById(id));
        }finally{pool.shutdownNow();}
    }
    Map<String,Object> draft(String kind){return new LinkedHashMap<>(Map.of("clientRequestId",UUID.randomUUID().toString(),"kind",kind,"title","QA 검수","content","<script>text only</script>\n검수할 원문","sourceUrl","https://example.test/source"));}
    @Test void reviewUsesImmutableSnapshotSecondReviewerAndExplicitClinicalAuthority()throws Exception{
        var d=draft("RISK_RULE");var r=post(admin,"/api/wc/admin/reviews",d,201);assertEquals(r.get("id"),post(admin,"/api/wc/admin/reviews",d,201).get("id"));String url="/api/wc/admin/reviews/"+r.get("id")+"/state";
        d.put("content","변경된 본문");post(admin,"/api/wc/admin/reviews",d,409);
        var submitted=put(admin,url,Map.of("action","SUBMIT","expectedVersion",r.get("version")),200);
        var decision=Map.of("action","APPROVE","expectedVersion",submitted.get("version"),"reason","QA 권한 검증 (임상 검수 아님)","evidenceUrl","https://example.test/evidence");
        put(admin,url,decision,403);put(administrator("review-other"),url,decision,403);
        var approved=put(expert,url,decision,200);assertEquals("APPROVED",approved.get("status"));assertEquals(r.get("contentHash"),approved.get("contentHash"));
        put(expert,url,decision,409);assertEquals(false,getData(expert,"/api/wc/admin/reviews/capabilities").get("publishEnabled"));
    }
    @Test void reviewPreservesWhitespaceAndHashesTheStoredTextExactly()throws Exception {
        var d=draft("QUESTION_BANK");String raw="  indented: source\n\n";d.put("content",raw);
        var r=post(admin,"/api/wc/admin/reviews",d,201);assertEquals(raw,r.get("content"));
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))),r.get("contentHash"));
    }
    @Test void rejectionNeedsReasonAndRevisionKeepsPreviousSnapshot()throws Exception{
        var d=draft("CARE_GUIDE");var r=post(admin,"/api/wc/admin/reviews",d,201);String url="/api/wc/admin/reviews/"+r.get("id")+"/state";r=put(admin,url,Map.of("action","SUBMIT","expectedVersion",r.get("version")),200);
        put(expert,url,Map.of("action","REJECT","expectedVersion",r.get("version"),"reason",""),400);
        put(expert,url,Map.of("action","REJECT","expectedVersion",r.get("version"),"reason","근거 보완 필요"),200);
        var revision=draft("CARE_GUIDE");revision.put("supersedesId",r.get("id"));var next=post(admin,"/api/wc/admin/reviews",revision,201);assertEquals(r.get("id"),next.get("supersedesId"));
    }
    @Test void administratorCreationRequiresAdminAndIsAudited()throws Exception {
        String newId="qa-"+UUID.randomUUID().toString().substring(0,12);
        var input=Map.of("adminId",newId,"name","QA 운영자","password","QaStrongPassword123!");
        post(owner,"/api/wc/admin/signup",input,403);post(admin,"/api/wc/admin/signup",input,200);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_events WHERE action='ADMIN_CREATE' AND target_id=?",Long.class,newId));
    }
    @Test void auditIsRoleProtectedAppendOnlyAndOmitsPrivateInquiryContent()throws Exception{
        String inquiry=inquiry();getData(admin,"/api/wc/admin/inquiries/"+inquiry);String log=json.writeValueAsString(getData(admin,"/api/wc/admin/audit"));
        assertTrue(log.contains("INQUIRY_READ"));assertFalse(log.contains("QA 민감 본문"));assertFalse(log.contains("password"));
        mvc.perform(get("/api/wc/admin/audit").header("Authorization",owner)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/wc/admin/audit/anything").header("Authorization",admin)).andExpect(status().isNotFound());
        post(owner,"/api/wc/admin/reviews",draft("CARE_GUIDE"),403);
    }
    @Test void publicSearchValidatesRequestsAndReportsConfigurationHonestly()throws Exception{
        var r=getData(owner,"/api/wc/hospitals/public?kind=HOSPITAL");assertEquals("UNCONFIGURED",r.get("status"));assertNull(r.get("total"));
        mvc.perform(get("/api/wc/hospitals/public").param("kind","PHARMACY").param("size","10000").header("Authorization",owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wc/hospitals/public").param("kind","HOSPITAL")).andExpect(status().isUnauthorized());
    }
    @Test void nearbySearchRequiresLoginValidCoordinatesAndDoesNotPersistLocation()throws Exception{
        String url="/api/wc/hospitals/public/nearby";
        var data=new HashMap<String,Object>(Map.of("kind","PHARMACY","latitude",37.5665,"longitude",126.978));
        long before=count("analysis_sessions","user_id",id);
        var r=post(owner,url,data,200);assertEquals("UNCONFIGURED",r.get("status"));assertEquals(before,count("analysis_sessions","user_id",id));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().isUnauthorized());
        data.put("latitude",91);post(owner,url,data,400);data.put("latitude",37.56);data.remove("longitude");post(owner,url,data,400);
        data.put("longitude",126.97);data.put("size",1000);post(owner,url,data,400);
    }
}
