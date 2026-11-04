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

class AnalysisFlowTests extends BackendApplicationTests {
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
        owner = account("analysis-owner"); other = account("analysis-other");
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
    @Test void sessionPersistsResumesAndCompletesWithGuide() throws Exception {
        var first = create(UUID.randomUUID().toString(), null);
        var resumed = data(mvc.perform(get("/api/wc/analyses/" + first.get("id")).header("Authorization", owner))
                .andExpect(status().isOk()).andReturn());
        assertEquals(first.get("question"), resumed.get("question"));
        var next = answer(resumed, UUID.randomUUID().toString(), "none", 200);
        var done = answer(next, UUID.randomUUID().toString(), null, 200);
        assertEquals("COMPLETED", done.get("status")); assertEquals("UNKNOWN", done.get("level"));
        assertEquals(2, ((List<?>) done.get("answers")).size());
        assertEquals(true, ((Map<?, ?>) done.get("result")).get("guide_available"));
        mvc.perform(get("/api/wc/analyses/" + first.get("id") + "/image").header("Authorization", owner))
                .andExpect(status().isOk());
    }
    @Test void creationAndAnswersAreIdempotentButChangedPayloadConflicts() throws Exception {
        String createId = UUID.randomUUID().toString(); var first = create(createId, null);
        assertEquals(first.get("id"), create(createId, null).get("id")); verify(ai, times(1)).predict(any(), anyString());
        String answerId = UUID.randomUUID().toString(); var next = answer(first, answerId, "none", 200);
        var replay = answer(first, answerId, "none", 200);
        assertEquals(next.get("version"), replay.get("version")); assertEquals(1, answers.count());
        answer(first, answerId, "breath", 409);
        answer(first, UUID.randomUUID().toString(), "none", 409);
        answer(next, UUID.randomUUID().toString(), "not-a-choice", 400);
    }
    @Test void otherUsersCannotReadMutateDeleteOrAttachToRecord() throws Exception {
        var first = create(UUID.randomUUID().toString(), null); String url = "/api/wc/analyses/" + first.get("id");
        mvc.perform(get(url).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get(url + "/image").header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(delete(url).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(post(url + "/answers").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID().toString(), "expectedVersion", 1, "questionId", "common.systemic", "answer", "breath"))))
                .andExpect(status().isNotFound());
        mvc.perform(post(url + "/retry").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/wc/analyses").file(new MockMultipartFile("file", "x.png", "image/png", photo))
                .param("clientRequestId", UUID.randomUUID().toString()).param("parentId", (String) first.get("id")).header("Authorization", other))
                .andExpect(status().isNotFound());
        assertEquals("[]", mvc.perform(get("/api/wc/analyses").header("Authorization", other)).andReturn().getResponse().getContentAsString());
    }
    @Test void unavailableAiPreservesPhotoAndCanRetry() throws Exception {
        when(ai.predict(any(), anyString())).thenThrow(new AiUnavailableException());
        var failed = create(UUID.randomUUID().toString(), null); assertEquals("RETRY_REQUIRED", failed.get("status"));
        doReturn(Map.of("quality", Map.of("ok", false))).when(ai).predict(any(), anyString());
        var resumed = data(mvc.perform(post("/api/wc/analyses/" + failed.get("id") + "/retry").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedVersion", failed.get("version")))))
                .andExpect(status().isOk()).andReturn());
        assertEquals("QUESTIONNAIRE", resumed.get("status"));
        mvc.perform(get("/api/wc/analyses/" + failed.get("id") + "/image").header("Authorization", owner)).andExpect(status().isOk());
    }
    @Test void failedAnswerDoesNotConsumeTurnOrWriteHistory() throws Exception {
        var first = create(UUID.randomUUID().toString(), null);
        doThrow(new AiUnavailableException()).when(ai).question(anyMap());
        var failed = answer(first, UUID.randomUUID().toString(), "none", 200);
        assertEquals("RETRY_REQUIRED", failed.get("status")); assertEquals(0, failed.get("turn")); assertEquals(0, answers.count());
        assertEquals(first.get("question"), failed.get("question"));
    }
    @Test void emergencyIsSavedEvenWhenGuideFailsAndDoesNotCallAnalyze() throws Exception {
        var first = create(UUID.randomUUID().toString(), null);
        when(ai.guide(anyMap())).thenThrow(new AiUnavailableException());
        var done = answer(first, UUID.randomUUID().toString(), "breath", 200);
        assertEquals("EMERGENCY", done.get("level")); assertEquals("COMPLETED", done.get("status"));
        assertEquals(false, ((Map<?, ?>) done.get("result")).get("guide_available"));
        verify(ai, never()).analyze(anyMap());
        verify(ai, never()).guide(anyMap());
    }
    @Test void caseTimelineAndDeletionAreOwnerScoped() throws Exception {
        var first = create(UUID.randomUUID().toString(), null);
        answer(first, UUID.randomUUID().toString(), "none", 200);
        var followup = create(UUID.randomUUID().toString(), (String) first.get("id"));
        assertEquals(first.get("caseId"), followup.get("caseId"));
        var response = mvc.perform(get("/api/wc/analyses").param("caseId", (String) first.get("caseId")).header("Authorization", owner))
                .andExpect(status().isOk()).andReturn();
        assertEquals(2, json.readValue(response.getResponse().getContentAsString(), List.class).size());
        mvc.perform(delete("/api/wc/analyses/" + first.get("id")).header("Authorization", owner)).andExpect(status().isNoContent());
        assertEquals(1,erasureJdbc.queryForObject("SELECT COUNT(*) FROM analysis_erasure_receipts WHERE session_id=?",Long.class,first.get("id")));
        assertEquals(0, answers.count());
        mvc.perform(get("/api/wc/analyses/" + first.get("id") + "/image").header("Authorization", owner)).andExpect(status().isNotFound());
    }
    @Test void rejectMissingAuthAndUnsupportedImage() throws Exception {
        mvc.perform(get("/api/wc/analyses")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/wc/analyses").file(new MockMultipartFile("file", "x.jpg", "image/jpeg", "not an image".getBytes()))
                .param("clientRequestId", UUID.randomUUID().toString()).header("Authorization", owner)).andExpect(status().isUnsupportedMediaType());
    }
    @Test void concurrentAnswersCannotOverwriteEachOther() throws Exception {
        var first = create(UUID.randomUUID().toString(), null);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> submit = () -> {
                gate.await();
                return mvc.perform(post("/api/wc/analyses/" + first.get("id") + "/answers").header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "clientRequestId", UUID.randomUUID().toString(), "expectedVersion", first.get("version"),
                        "questionId", "common.systemic", "answer", "none"))))
                    .andReturn().getResponse().getStatus();
            };
            var a = pool.submit(submit); var b = pool.submit(submit); gate.countDown();
            var statuses = new ArrayList<>(List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(200, 409), statuses); assertEquals(1, answers.count());
        } finally { pool.shutdownNow(); }
    }
    @Test void browserCorsAndBadInputHaveExpectedResponses() throws Exception {
        mvc.perform(options("/api/wc/analyses").header("Origin", "http://127.0.0.1:8081")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Access-Control-Allow-Origin", "http://127.0.0.1:8081"));
        mvc.perform(options("/api/wc/analyses").header("Origin", "https://not-allowed.example")
            .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        mvc.perform(get("/api/wc/analyses").param("page", "abc").header("Authorization", owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wc/analyses").param("size", "5000").header("Authorization", owner)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/wc/analyses/any/answers").header("Authorization", owner)
            .contentType(MediaType.APPLICATION_JSON).content("{bad json")).andExpect(status().isBadRequest());
    }


    Map<String, Object> progress(Map<String,Object> session, String token, Object pain, String note, int expected) throws Exception {
        var body = new LinkedHashMap<String,Object>(); body.put("expectedVersion",session.get("version"));
        body.put("painScore",pain); body.put("note",note);
        return data(mvc.perform(put("/api/wc/analyses/"+session.get("id")+"/progress").header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn());
    }
    @Test void progressPersistsWithoutChangingQuestionnaireAndSupportsSafeReplayAndClear() throws Exception {
        var first=create(UUID.randomUUID().toString(),null);
        assertEquals("", ((Map<?,?>)first.get("progress")).get("note")); assertNull(((Map<?,?>)first.get("progress")).get("painScore"));
        var saved=progress(first,owner,4,"  사용자 경과 기록  ",200);
        assertEquals(Map.of("painScore",4,"note","사용자 경과 기록"),saved.get("progress"));
        assertEquals(first.get("question"),saved.get("question"));assertEquals(first.get("vision"),saved.get("vision"));
        assertEquals(first.get("turn"),saved.get("turn"));assertEquals(first.get("status"),saved.get("status"));
        assertEquals(saved.get("version"),progress(first,owner,4,"사용자 경과 기록",200).get("version"));
        progress(first,owner,7,"stale overwrite",409);
        var loaded=data(mvc.perform(get("/api/wc/analyses/"+first.get("id")).header("Authorization",owner)).andReturn());
        assertEquals(saved.get("progress"),loaded.get("progress"));
        var cleared=progress(saved,owner,null,null,200);
        assertNull(((Map<?,?>)cleared.get("progress")).get("painScore"));assertEquals("",((Map<?,?>)cleared.get("progress")).get("note"));
        verify(ai,times(1)).predict(any(),anyString());verify(ai,times(1)).question(anyMap());verify(ai,never()).analyze(anyMap());
    }
    @Test void progressValidatesRangeFractionLengthAndOwnership() throws Exception {
        var first=create(UUID.randomUUID().toString(),null);
        progress(first,owner,-1,"",400);progress(first,owner,11,"",400);progress(first,owner,1.5,"",400);
        progress(first,owner,5,"x".repeat(2001),400);progress(first,other,5,"",404);
        mvc.perform(put("/api/wc/analyses/"+first.get("id")+"/progress").header("Authorization",owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"painScore\":4}")).andExpect(status().isBadRequest());
        assertEquals(((Number)first.get("version")).longValue(),sessions.findById((String)first.get("id")).orElseThrow().getVersion());
    }
    @Test void comparisonScopesCaseAndOwnerAndUsesEarliestRemainingRecordAfterDeletion() throws Exception {
        var first=create(UUID.randomUUID().toString(),null);
        var next=create(UUID.randomUUID().toString(),(String)first.get("id"));
        var separate=create(UUID.randomUUID().toString(),null);
        var foreign=sessions.saveAndFlush(new skincare.domain.analysis.AnalysisSession("analysis-other",UUID.randomUUID().toString(),(String)first.get("caseId"),photo,"image/png"));
        String url="/api/wc/analyses/"+next.get("id")+"/comparison";
        var pair=data(mvc.perform(get(url).header("Authorization",owner)).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store")).andReturn());
        assertEquals(first.get("id"),((Map<?,?>)pair.get("reference")).get("id"));assertEquals(2,pair.get("recordCount"));
        assertEquals(false,pair.get("sameRecord"));assertFalse(((Map<?,?>)pair.get("current")).containsKey("answers"));
        mvc.perform(get(url).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(get(url).param("referenceId",foreign.getId()).header("Authorization",owner)).andExpect(status().isNotFound());
        mvc.perform(get(url).param("referenceId",(String)separate.get("id")).header("Authorization",owner)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/wc/analyses/"+first.get("id")).header("Authorization",owner)).andExpect(status().isNoContent());
        var remaining=data(mvc.perform(get(url).header("Authorization",owner)).andExpect(status().isOk()).andReturn());
        assertEquals(true,remaining.get("sameRecord"));assertEquals(1,remaining.get("recordCount"));
    }
    @Test void concurrentProgressEditsCannotOverwrite() throws Exception {
        var first=create(UUID.randomUUID().toString(),null);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.function.Function<Integer,java.util.concurrent.Callable<Integer>> task=pain->()->{
                gate.await();return mvc.perform(put("/api/wc/analyses/"+first.get("id")+"/progress").header("Authorization",owner)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedVersion",first.get("version"),"painScore",pain,"note","concurrent fixture"))))
                    .andReturn().getResponse().getStatus();
            };
            var a=pool.submit(task.apply(2));var b=pool.submit(task.apply(8));gate.countDown();
            var statuses=new ArrayList<>(List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        } finally {pool.shutdownNow();}
    }
    @Test void operationsAreAdminOnlyAndNeverExposePhotosAnswersOrProgress() throws Exception {
        var first=create(UUID.randomUUID().toString(),null);progress(first,owner,8,"private note",200);
        var a=admins.findById("operations-admin").orElseGet(()->skincare.domain.admin.entity.Admin.builder().adminId("operations-admin").name("QA").password("unused").build());
        admins.saveAndFlush(a);String admin="Bearer "+jwt.createToken(a.getAdminId(),"ADMIN",a.currentTokenVersion());
        String url="/api/wc/admin/operations";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());mvc.perform(get(url).header("Authorization",owner)).andExpect(status().isForbidden());
        var result=data(mvc.perform(get(url).header("Authorization",admin)).andExpect(status().isOk()).andReturn());
        assertEquals(1,((Map<?,?>)result.get("counts")).get("QUESTIONNAIRE"));
        var item=(Map<?,?>)((List<?>)result.get("items")).get(0);
        assertEquals(Set.of("id","status","turn","errorCode","createdAt","updatedAt"),item.keySet());
        assertEquals(0, data(mvc.perform(get(url).param("status","COMPLETED").header("Authorization",admin)).andReturn()).get("total"));
        mvc.perform(get(url).param("status","UNKNOWN").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get(url).param("size","5000").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wc/analyses/"+first.get("id")+"/image").header("Authorization",admin)).andExpect(status().isForbidden());
        when(aiStatus.status()).thenReturn(Map.of("status","UNAVAILABLE","checkedAt","2026-10-31T00:00:00Z"));
        assertEquals("UNAVAILABLE",data(mvc.perform(get(url+"/ai-status").header("Authorization",admin)).andExpect(status().isOk()).andReturn()).get("status"));
        mvc.perform(get(url+"/ai-status").header("Authorization",owner)).andExpect(status().isForbidden());
    }
}
