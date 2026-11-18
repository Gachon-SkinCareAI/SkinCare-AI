package backend.skincare;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import skincare.domain.admin.entity.Admin;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;

/** 관리자 화면에서 AI 성능 지표를 등록·수정·삭제하는 API 검증. */
class AdminAiMetricTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired AdminRepository admins;
    @Autowired AiModelMetricsRepository metrics;
    @Autowired AuditEventRepository audits;
    @Autowired JwtTokenProvider jwt;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    String admin;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        metrics.deleteAll();
        var a =
                admins.findById("metric-admin")
                        .orElseGet(() -> Admin.builder().adminId("metric-admin").name("지표 검증 관리자").password(encoder.encode("ServiceTest123!")).build());
        admins.saveAndFlush(a);
        admin = "Bearer " + jwt.createToken(a.getAdminId(), "ADMIN", a.currentTokenVersion());
    }

    private ResultActions save(String key, String body) throws Exception {
        return mvc.perform(
                put("/api/wc/admin/ai/metrics/" + key)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void anonymousAndNonAdminCannotChangeMetrics() throws Exception {
        mvc.perform(put("/api/wc/admin/ai/metrics/macro_f1").contentType(MediaType.APPLICATION_JSON).content("{\"value\":90}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/wc/admin/ai/metrics/macro_f1")).andExpect(status().isUnauthorized());
        assertEquals(0, metrics.count());
    }

    @Test
    void savingTheSameKeyAgainKeepsOneRowAndRecordsTheChange() throws Exception {
        save("macro_f1", "{\"value\":93.1,\"unit\":\"%\",\"description\":\"잠금 test 177장\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric.value").value(93.1))
                .andExpect(jsonPath("$.metric.unit").value("%"))
                .andExpect(jsonPath("$.metric.change").doesNotExist());

        save("macro_f1", "{\"value\":94,\"unit\":\"%\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric.change").value("+0.9%"));
        save("macro_f1", "{\"value\":92,\"unit\":\"%\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric.change").value("-2%"));

        assertEquals(1, metrics.count());
        mvc.perform(get("/api/wc/admin/ai/metrics").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics_summary.macro_f1.value").value(92.0))
                .andExpect(jsonPath("$.metrics_summary.macro_f1.change").value("-2%"));
        assertTrue(audits.findAll(org.springframework.data.domain.Pageable.unpaged()).getContent().stream().anyMatch(e -> "AI_METRIC_SAVE".equals(e.getAction())));
    }

    @Test
    void invalidInputIsRejectedWithoutSaving() throws Exception {
        save("Macro_F1", "{\"value\":90}").andExpect(status().isBadRequest());
        save("a", "{\"value\":90}").andExpect(status().isBadRequest());
        save("macro_f1", "{\"value\":101,\"unit\":\"%\"}").andExpect(status().isBadRequest());
        save("macro_f1", "{\"value\":-1}").andExpect(status().isBadRequest());
        save("macro_f1", "{\"unit\":\"%\"}").andExpect(status().isBadRequest());
        save("macro_f1", "{\"value\":90,\"unit\":\"" + "x".repeat(21) + "\"}").andExpect(status().isBadRequest());
        assertEquals(0, metrics.count());
    }

    @Test
    void deleteRemovesTheMetricAndMissingKeysAre404() throws Exception {
        save("burn_recall", "{\"value\":96.5,\"unit\":\"%\"}").andExpect(status().isOk());
        mvc.perform(delete("/api/wc/admin/ai/metrics/burn_recall").header("Authorization", admin)).andExpect(status().isOk());
        assertEquals(0, metrics.count());
        mvc.perform(delete("/api/wc/admin/ai/metrics/burn_recall").header("Authorization", admin)).andExpect(status().isNotFound());
        assertTrue(audits.findAll(org.springframework.data.domain.Pageable.unpaged()).getContent().stream().anyMatch(e -> "AI_METRIC_DELETE".equals(e.getAction())));
    }
}
