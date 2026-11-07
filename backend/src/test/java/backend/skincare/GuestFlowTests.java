package backend.skincare;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import skincare.external.*;
import skincare.domain.user.entity.User;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestFlowTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AnalysisSessionRepository sessions;
    @Autowired AnalysisAnswerRepository answers;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper json;
    @MockitoBean AnalysisAiClient ai;
    @MockitoBean AiStatusClient aiStatus;
    @Autowired AdminRepository admins;
    @Autowired org.springframework.jdbc.core.JdbcTemplate erasureJdbc;
    MockMvc mvc;
    String owner, other;
    byte[] photo;
    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        answers.deleteAll(); sessions.deleteAll();
        owner = account("guest-test-member"); other = account("guest-test-other");
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
        photo = output.toByteArray(); reset(ai);
        when(ai.predict(any(), anyString())).thenReturn(Map.of("quality", Map.of("ok", true), "classification",
                Map.of("label", "UNKNOWN", "gate_fail", List.of("no_roi"))));
        when(ai.question(anyMap())).thenAnswer(call -> {
            Map<String, Object> request = call.getArgument(0);
            var slots = new LinkedHashMap<>((Map<String, Object>) request.get("slots"));
            String selected = (String) request.get("answer");
            if (selected != null) slots.put("systemic", List.of(selected));
            if ("breath".equals(selected)) return Map.of("action", "finish", "slots", slots,
                    "level", "EMERGENCY", "rule_ids", List.of("sys.breath"));
            if (slots.isEmpty()) return Map.of("action", "ask", "question_id", "common.systemic", "utterance", "증상을 선택해 주세요.",
                    "choices", List.of(Map.of("value", "none", "label", "없음"), Map.of("value", "breath", "label", "호흡 곤란")), "slots", slots);
            if ("common.finish_confirm".equals(request.get("last_question_id")))
                return Map.of("action", "finish", "slots", slots, "level", "UNKNOWN", "rule_ids", List.of("vision.unknown"));
            return Map.of("action", "ask", "question_id", "common.finish_confirm", "utterance", "완료할까요?", "choices", List.of(), "slots", slots);
        });
        when(ai.analyze(anyMap())).thenReturn(Map.of("level", "UNKNOWN", "rule_ids", List.of("vision.unknown")));
        when(ai.guide(anyMap())).thenReturn(Map.of("cards", List.of(Map.of("title", "확인 안내", "body", "의료기관에 문의하세요.", "source_url", "https://example.test"))));
    }
    String account(String id) {
        var user = users.findById(id).orElseGet(() -> User.builder().provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).userId(id).email(id + "@example.test").nickname(id).build());
        user.acceptConsents("2026-11-03", "2026-11-03"); user.acceptHealthConsent("2026-11-03"); users.saveAndFlush(user);
        return "Bearer " + jwt.createToken(id, "USER", user.currentTokenVersion());
    }
    Map<String, Object> data(MvcResult result) throws Exception { return json.readValue(result.getResponse().getContentAsString(), Map.class); }
    Map<String, Object> create(String requestId, String parent) throws Exception {
        var request = multipart("/api/wc/analyses").file(new MockMultipartFile("file", "x.png", "image/png", photo))
                .param("clientRequestId", requestId).header("Authorization", owner);
        if (parent != null) request.param("parentId", parent);
        return data(mvc.perform(request).andExpect(status().isCreated()).andReturn());
    }
    Map<String, Object> answer(Map<String, Object> current, String requestId, String value, int statusCode) throws Exception {
        var question = (Map<?, ?>) current.get("question");
        var body = new LinkedHashMap<String, Object>();
        body.put("clientRequestId", requestId); body.put("expectedVersion", current.get("version"));
        body.put("questionId", question.get("question_id")); body.put("answer", value);
        return data(mvc.perform(post("/api/wc/analyses/" + current.get("id") + "/answers").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(statusCode)).andReturn());
    }

    String key(){return UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");}
    Map<String,Object> guest(String key,boolean training) throws Exception {
        return data(mvc.perform(post("/api/wc/auth/guest").contentType(MediaType.APPLICATION_JSON)
          .content(json.writeValueAsString(Map.of("deviceKey",key,"agreedService",true,"agreedHealthData",true,
            "policyVersion",skincare.service.ConsentPolicy.GUEST_VERSION,"trainingGranted",training,
            "trainingVersion",skincare.service.ConsentPolicy.TRAINING_VERSION)))).andExpect(status().isOk()).andReturn());
    }
    void training(boolean granted) throws Exception {
        mvc.perform(put("/api/wc/guest/consents/training").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
          .content(json.writeValueAsString(Map.of("granted",granted,"version",skincare.service.ConsentPolicy.TRAINING_VERSION))))
          .andExpect(status().isNoContent());
    }
    @Test void guestRequiresExplicitCurrentServiceAndHealthConsent() throws Exception {
        long before=users.count();
        for(var b:List.of(Map.of("deviceKey",key()),Map.of("deviceKey",key(),"agreedService",true,"agreedHealthData",false,"policyVersion",skincare.service.ConsentPolicy.GUEST_VERSION),
          Map.of("deviceKey",key(),"agreedService",true,"agreedHealthData",true,"policyVersion","old")))
          mvc.perform(post("/api/wc/auth/guest").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b))).andExpect(status().isBadRequest());
        assertEquals(before,users.count());
        mvc.perform(multipart("/api/wc/analyses").file(new MockMultipartFile("file","x.png","image/png",photo)).param("clientRequestId",UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
    }
    @Test void guestCanAnalyzeAnswerResumeAndReadOnlyOwnPhoto() throws Exception {
        String key=key();var identity=guest(key,false);owner="Bearer "+identity.get("token");
        var first=create(UUID.randomUUID().toString(),null);
        assertEquals("GUEST",first.get("sourceKind"));assertEquals(false,first.get("trainingEligible"));
        var next=answer(first,UUID.randomUUID().toString(),"none",200);
        var done=answer(next,UUID.randomUUID().toString(),null,200);
        assertEquals("COMPLETED",done.get("status"));assertEquals(2,((List<?>)done.get("answers")).size());
        var resumed=data(mvc.perform(post("/api/wc/auth/guest/resume").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("deviceKey",key)))).andExpect(status().isOk()).andReturn());
        assertEquals(identity.get("userId"),resumed.get("userId"));assertEquals(identity.get("userId"),guest(key,true).get("userId"));
        String url="/api/wc/analyses/"+first.get("id");
        mvc.perform(get(url+"/image").header("Authorization","Bearer "+resumed.get("token"))).andExpect(status().isOk());
        var another=guest(key(),false);
        for(String suffix:List.of("","/image"))mvc.perform(get(url+suffix).header("Authorization","Bearer "+another.get("token"))).andExpect(status().isNotFound());
        mvc.perform(delete(url).header("Authorization","Bearer "+another.get("token"))).andExpect(status().isNotFound());
        var saved=users.findById((String)identity.get("userId")).orElseThrow();
        assertNotEquals(key,saved.getGuestKeyHash());assertNull(saved.getEmail());assertFalse(saved.isTrainingGranted());
    }
    @Test void guestCannotUseMemberAdminOrGpsAndCannotForgeMemberRole() throws Exception {
        var g=guest(key(),false);owner="Bearer "+g.get("token");
        for(String url:List.of("/api/wc/users/me","/api/wc/admin/dashboard"))mvc.perform(get(url).header("Authorization",owner)).andExpect(status().isForbidden());
        mvc.perform(post("/api/wc/hospitals/public/nearby").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isForbidden());
        var u=users.findById((String)g.get("userId")).orElseThrow();
        mvc.perform(get("/api/wc/analyses").header("Authorization","Bearer "+jwt.createToken(u.getUserId(),"USER",u.currentTokenVersion()))).andExpect(status().isUnauthorized());
    }
    @Test void trainingIsSnapshotOnlyWithdrawalClearsEligibilityAndRegrantIsNotRetroactive() throws Exception {
        owner="Bearer "+guest(key(),false).get("token");
        var first=create(UUID.randomUUID().toString(),null);assertEquals(false,first.get("trainingEligible"));
        training(true);var second=create(UUID.randomUUID().toString(),null);
        assertEquals(true,second.get("trainingEligible"));assertEquals("PENDING",second.get("trainingReviewStatus"));
        training(false);assertFalse(sessions.findById((String)second.get("id")).orElseThrow().isTrainingEligible());
        training(true);assertFalse(sessions.findById((String)first.get("id")).orElseThrow().isTrainingEligible());
        assertEquals("WITHDRAWN",sessions.findById((String)second.get("id")).orElseThrow().getTrainingReviewStatus());
    }
    @Test void deletionRemovesPhotosAnswersAndCapabilityAndWritesBackupReceipt() throws Exception {
        String key=key();var g=guest(key,true);owner="Bearer "+g.get("token");
        var first=create(UUID.randomUUID().toString(),null);answer(first,UUID.randomUUID().toString(),"none",200);
        mvc.perform(delete("/api/wc/guest").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"wrong\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/wc/guest").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"삭제\"}")).andExpect(status().isNoContent());
        assertFalse(users.existsById((String)g.get("userId")));assertEquals(0,sessions.count());assertEquals(0,answers.count());
        assertEquals(1,erasureJdbc.queryForObject("SELECT COUNT(*) FROM account_erasure_receipts WHERE user_id=?",Long.class,g.get("userId")));
        mvc.perform(get("/api/wc/analyses").header("Authorization",owner)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wc/auth/guest/resume").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("deviceKey",key)))).andExpect(status().isUnauthorized());
    }
    @Test void publicConsentDocumentsAreReadableWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/wc/auth/guest/policy")).andExpect(status().isOk());
        for(String name:List.of("guest","training","location")) {
          String html=mvc.perform(get("/api/wc/policies/2026-11-04/"+name)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
          assertTrue(html.contains("wanjang0320@gachon.ac.kr"));assertTrue(html.contains("5년"));
        }
    }
    @Test void signupLocationIsOptionalAndServerRejectsGpsAfterWithdrawal() throws Exception {
        String url="/api/wc/users/me/consents";
        var base=new HashMap<String,Object>(Map.of("agreedTerms",true,"agreedPrivacy",true,"agreedHealthData",true,
          "termsVersion",skincare.service.ConsentPolicy.VERSION,"privacyVersion",skincare.service.ConsentPolicy.VERSION,
          "healthDataVersion",skincare.service.ConsentPolicy.HEALTH_VERSION,"agreedLocation",false,"locationVersion",skincare.service.ConsentPolicy.LOCATION_VERSION));
        mvc.perform(put(url).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base))).andExpect(status().isOk());
        String nearby="/api/wc/hospitals/public/nearby",body="{\"kind\":\"PHARMACY\",\"latitude\":37.5,\"longitude\":127.0}";
        mvc.perform(post(nearby).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        base.put("agreedLocation",true);mvc.perform(put(url).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base))).andExpect(status().isOk());
        mvc.perform(post(nearby).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put(url+"/location").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("granted",false,"version",skincare.service.ConsentPolicy.LOCATION_VERSION)))).andExpect(status().isNoContent());
        mvc.perform(post(nearby).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }
}
