package backend.skincare;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import skincare.domain.user.entity.User;
import skincare.domain.admin.entity.Admin;
import skincare.domain.analysis.AnalysisSession;
import skincare.domain.hospital.entity.Hospital;
import skincare.domain.hospital.entity.ENUM.HospitalType;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BasicServiceFlowTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users; @Autowired UserSettingsRepository settings;
    @Autowired AdminRepository admins; @Autowired InquiryRepository inquiries;
    @Autowired InquiryAnswerRepository answers; @Autowired HospitalRepository hospitals;
    @Autowired AnalysisSessionRepository analyses; @Autowired JwtTokenProvider jwt;
    @Autowired PasswordEncoder encoder; @Autowired ObjectMapper json;
    MockMvc mvc; String owner,other,admin;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        answers.deleteAll();inquiries.deleteAll();hospitals.deleteAll();
        owner=account("svc-owner");other=account("svc-other");
        var a=admins.findById("svc-admin").orElseGet(()->Admin.builder().adminId("svc-admin").name("검증 관리자").password(encoder.encode("ServiceTest123!")).build());
        admins.saveAndFlush(a);admin="Bearer "+jwt.createToken(a.getAdminId(),"ADMIN",a.currentTokenVersion());
    }
    String account(String id) {
        var u=users.findById(id).orElseGet(()->User.builder().provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).userId(id).email(id+"@example.test").nickname("검증 사용자").password(encoder.encode("ServiceTest123!")).build());
        u.acceptConsents("2026-11-03","2026-11-03"); u.acceptHealthConsent("2026-11-03");users.saveAndFlush(u);
        return "Bearer "+jwt.createToken(id,"USER",u.currentTokenVersion());
    }
    Map<String,Object> body(MvcResult r)throws Exception{String text=r.getResponse().getContentAsString();return text.isBlank()?Map.of():json.readValue(text,Map.class);}
    Map<String,Object> postJson(String token,String url,Object data,int expected)throws Exception{
        return body(mvc.perform(post(url).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().is(expected)).andReturn());
    }
    Map<String,Object> putJson(String token,String url,Object data,int expected)throws Exception{
        var result=mvc.perform(put(url).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().is(expected)).andReturn();
        return expected==204 ? Map.of() : body(result);
    }
    Map<String,Object> inquiryInput(){return new LinkedHashMap<>(Map.of("clientRequestId",UUID.randomUUID().toString(),"type","SYSTEM_ERROR","title","검증 문의","content","저장한 기록을 확인하고 싶습니다."));}
    Map<String,Object> hospitalInput(){return new LinkedHashMap<>(Map.of("clientRequestId",UUID.randomUUID().toString(),"name","검증용기관 실제아님","type","DERMATOLOGY","region","검증지역","address","테스트 주소","nightCare",false,"emergency",true,"dataSource","자동화 테스트","sourceUrl","https://example.test/source","verifiedOn","2026-01-01"));}
    @Test void profileSettingsPersistAndBlankNicknameIsRejected()throws Exception{
        mvc.perform(get("/api/wc/users/me").header("Authorization",owner)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        putJson(owner,"/api/wc/users/me",Map.of("nickname","  저장 닉네임  "),200);
        var profile=body(mvc.perform(get("/api/wc/users/me").header("Authorization",owner)).andReturn());assertEquals("저장 닉네임",profile.get("nickname"));assertFalse(profile.containsKey("password"));assertFalse(profile.containsKey("token"));
        putJson(owner,"/api/wc/users/me",Map.of("nickname","   "),400);
        // Accounts created before the settings row existed can save preferences.
        settings.findById("svc-owner").ifPresent(settings::delete);settings.flush();
        putJson(owner,"/api/wc/users/me/settings",Map.of("pushDiagnosisAlert",false,"pushRecoveryReminder",true),200);
        var prefs=body(mvc.perform(get("/api/wc/users/me/settings").header("Authorization",owner)).andReturn());
        assertEquals(false,prefs.get("pushDiagnosisAlert"));assertEquals(true,prefs.get("pushRecoveryReminder"));
    }
    @Test void passwordAuthenticationIsGoneAndDoesNotMutateTheAccount()throws Exception{
        String before=users.findById("svc-owner").orElseThrow().getPassword();
        putJson(owner,"/api/wc/users/me/password",Map.of("currentPassword","ServiceTest123!","newPassword","NewService123!"),410);
        assertEquals(before,users.findById("svc-owner").orElseThrow().getPassword());
        mvc.perform(get("/api/wc/users/me").header("Authorization",owner)).andExpect(status().isOk());
    }
    @Test void inquiryReplayOwnershipAndAdminAnswerRoundTrip()throws Exception{
        var input=inquiryInput();var created=postJson(owner,"/api/wc/inquiries",input,201);String id=(String)created.get("id");
        assertEquals(id,postJson(owner,"/api/wc/inquiries",input,201).get("id"));assertEquals(1,inquiries.count());
        input.put("content","다른 내용");postJson(owner,"/api/wc/inquiries",input,409);
        mvc.perform(get("/api/wc/inquiries/"+id).header("Authorization",other)).andExpect(status().isNotFound());
        var list=body(mvc.perform(get("/api/wc/inquiries").header("Authorization",other)).andReturn());assertEquals(0,((List<?>)list.get("items")).size());
        mvc.perform(get("/api/wc/admin/inquiries").header("Authorization",owner)).andExpect(status().isForbidden());
        var reply=Map.of("clientRequestId",UUID.randomUUID().toString(),"expectedVersion",created.get("version"),"content","서비스 검증 답변입니다.");
        var done=putJson(admin,"/api/wc/admin/inquiries/"+id+"/answer",reply,200);assertEquals("COMPLETED",done.get("status"));
        putJson(admin,"/api/wc/admin/inquiries/"+id+"/answer",reply,200);assertEquals(1,answers.count());
        var got=body(mvc.perform(get("/api/wc/inquiries/"+id).header("Authorization",owner)).andReturn());
        assertEquals("서비스 검증 답변입니다.",((Map<?,?>)got.get("answer")).get("content"));
        putJson(admin,"/api/wc/admin/inquiries/"+id+"/answer",Map.of("clientRequestId",UUID.randomUUID().toString(),"expectedVersion",0,"content","다른 답변"),409);
    }
    @Test void inquiryCannotLinkAnotherUsersAnalysisAndSurvivesOwnPhotoDeletion()throws Exception{
        var analysis=analyses.saveAndFlush(new AnalysisSession("svc-owner",UUID.randomUUID().toString(),null,new byte[]{1},"image/png"));
        var input=inquiryInput();input.put("analysisId",analysis.getId());
        postJson(other,"/api/wc/inquiries",input,404);
        var mine=postJson(owner,"/api/wc/inquiries",input,201);
        analyses.deleteById(analysis.getId());analyses.flush();
        mvc.perform(get("/api/wc/inquiries/"+mine.get("id")).header("Authorization",owner)).andExpect(status().isOk());
    }
    @Test void concurrentAdminAnswersCannotOverwriteEachOther()throws Exception{
        var created=postJson(owner,"/api/wc/inquiries",inquiryInput(),201);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            List<Future<Integer>> results=new ArrayList<>();
            for(int n=0;n<2;n++){final int number=n;results.add(pool.submit(()->{start.await();return mvc.perform(put("/api/wc/admin/inquiries/"+created.get("id")+"/answer").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clientRequestId",UUID.randomUUID().toString(),"expectedVersion",created.get("version"),"content","동시 답변 "+number)))).andReturn().getResponse().getStatus();}));}
            start.countDown();var codes=new ArrayList<Integer>();for(var f:results)codes.add(f.get(20,TimeUnit.SECONDS));Collections.sort(codes);assertEquals(List.of(200,409),codes);assertEquals(1,answers.count());
        }finally{pool.shutdownNow();}
    }
    @Test void onlyReviewedActiveHospitalsArePublishedAndUpdatesDetectStaleVersion()throws Exception{
        hospitals.saveAndFlush(Hospital.builder().hospitalId("legacy-unverified").name("검수 전").hospitalType(HospitalType.OTHER).region("검증지역").address("검증 주소").build());
        var input=hospitalInput();input.put("status","ACTIVE");var created=postJson(admin,"/api/wc/admin/hospitals",input,201);
        assertEquals(created.get("id"),postJson(admin,"/api/wc/admin/hospitals",input,201).get("id"));assertEquals(2,hospitals.count());
        var found=body(mvc.perform(get("/api/wc/hospitals").param("q","검증지역").param("type","DERMATOLOGY").header("Authorization",owner)).andExpect(status().isOk()).andReturn());
        assertEquals(1,((List<?>)found.get("items")).size());
        assertEquals(0,((List<?>)body(mvc.perform(get("/api/wc/hospitals").param("q","%").header("Authorization",owner)).andReturn()).get("items")).size());
        input.put("expectedVersion",created.get("version"));input.put("status","INACTIVE");var updated=putJson(admin,"/api/wc/admin/hospitals/"+created.get("id"),input,200);
        assertEquals("INACTIVE",updated.get("status"));putJson(admin,"/api/wc/admin/hospitals/"+created.get("id"),input,409);
        assertEquals(0,((List<?>)body(mvc.perform(get("/api/wc/hospitals").header("Authorization",owner)).andReturn()).get("items")).size());
    }
    @Test void concurrentCreatesWithSameHospitalIdNeverOverwrite()throws Exception{
        String request=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Map.Entry<Integer,String>>> jobs=new ArrayList<>();
            for(int n=0;n<2;n++){final int number=n;jobs.add(pool.submit(()->{var input=hospitalInput();input.put("clientRequestId",request);input.put("status","ACTIVE");input.put("name","경합 검증 "+number);start.await();
                int code=mvc.perform(post("/api/wc/admin/hospitals").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andReturn().getResponse().getStatus();return Map.entry(code,(String)input.get("name"));}));}
            start.countDown();List<Integer> codes=new ArrayList<>();String winner=null;
            for(var job:jobs){var result=job.get(20,TimeUnit.SECONDS);codes.add(result.getKey());if(result.getKey()==201)winner=result.getValue();}
            Collections.sort(codes);assertEquals(List.of(201,409),codes);assertEquals(1,hospitals.count());assertEquals(winner,hospitals.findById(request).orElseThrow().getName());
        }finally{pool.shutdownNow();}
    }
    @Test void malformedLoginBodiesReturnClientErrorsInsteadOfServerErrors()throws Exception{
        mvc.perform(post("/api/wc/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/wc/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isGone());
    }
    @Test void hospitalValidationAndRoleBoundariesAreEnforced()throws Exception{
        var input=hospitalInput();input.put("status","ACTIVE");postJson(owner,"/api/wc/admin/hospitals",input,403);
        input.put("sourceUrl","javascript:alert(1)");postJson(admin,"/api/wc/admin/hospitals",input,400);
        input.put("sourceUrl","https://example.test/source");input.put("verifiedOn",LocalDate.now().plusYears(1).toString());postJson(admin,"/api/wc/admin/hospitals",input,400);
        mvc.perform(get("/api/wc/hospitals").param("size","1000").header("Authorization",owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wc/inquiries").param("page","-1").header("Authorization",owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wc/admin/dashboard").header("Authorization",owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/wc/admin/dashboard").header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(get("/api/wc/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/index.html")).andExpect(status().isOk());
        mvc.perform(get("/admin/app.js")).andExpect(status().isOk());
    }
}
