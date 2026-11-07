package backend.skincare;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.admin.entity.Admin;
import skincare.repository.*;
import skincare.security.JwtTokenProvider;
import skincare.service.PublishedPolicies;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:readiness;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "skincare.consent.terms-policy-url=","skincare.consent.privacy-policy-url=","skincare.consent.training-policy-url=https://",
 "skincare.medical.service-key=MEDICAL-SECRET-DO-NOT-RETURN","skincare.review.clinical-reviewers=missing-reviewer-id"})
class ServiceReadinessTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;@Autowired UserRepository users;@Autowired AdminRepository admins;
    @Autowired JdbcTemplate jdbc;@Autowired JwtTokenProvider jwt;
    MockMvc mvc;String id,token,admin;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();id=UUID.randomUUID().toString();
        var u=users.saveAndFlush(User.builder().userId(id).nickname("QA").provider(Provider.GOOGLE).build());token="Bearer "+jwt.createToken(id,"USER",u.currentTokenVersion());
        var a=admins.findById("readiness-qa").orElseGet(()->admins.saveAndFlush(Admin.builder().adminId("readiness-qa").name("QA").password("unused").build()));admin="Bearer "+jwt.createToken(a.getAdminId(),"ADMIN",a.currentTokenVersion());
    }
    @Test void publicManifestFailsClosedWithoutUrlsAndNeverExposesSecrets()throws Exception{
        String response=mvc.perform(get("/api/wc/auth/consent-policy")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.requiredAvailable").value(false)).andExpect(jsonPath("$.terms.url").isEmpty()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("MEDICAL-SECRET"));assertFalse(response.contains("missing-reviewer-id"));
    }
    @Test void directConsentRequestCannotBypassMissingDocumentsAndDoesNotCreateEvents()throws Exception{
        mvc.perform(put("/api/wc/users/me/consents").header("Authorization",token).contentType("application/json")
          .content("{\"agreedTerms\":true,\"agreedPrivacy\":true,\"termsVersion\":\"2026-11-03\",\"privacyVersion\":\"2026-11-03\",\"agreedHealthData\":true,\"healthDataVersion\":\"2026-11-03\"}"))
          .andExpect(status().isConflict());
        assertFalse(users.findById(id).orElseThrow().hasRequiredConsents());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consent_events WHERE user_id=?",Long.class,id));
    }
    @Test void malformedTrainingUrlBlocksOptInButAllowsWithdrawal()throws Exception{
        var u=users.findById(id).orElseThrow();u.acceptConsents("2026-11-03","2026-11-03");u.acceptHealthConsent("2026-11-03");u.trainingConsent(true,"2026-11-01");users.saveAndFlush(u);
        mvc.perform(put("/api/wc/users/me/consents/training").header("Authorization",token).contentType("application/json").content("{\"granted\":true,\"version\":\"2026-11-04\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/wc/users/me/consents/training").header("Authorization",token).contentType("application/json").content("{\"granted\":false,\"version\":\"2026-11-04\"}")).andExpect(status().isNoContent());
        assertFalse(users.findById(id).orElseThrow().isTrainingGranted());
    }
    @Test void readinessIsAdminOnlyAndConfiguredDoesNotMeanVerified()throws Exception{
        mvc.perform(get("/api/wc/admin/operations/readiness").header("Authorization",token)).andExpect(status().isForbidden());
        String body=mvc.perform(get("/api/wc/admin/operations/readiness").header("Authorization",admin)).andExpect(status().isOk())
          .andExpect(jsonPath("$.externalConnectionsVerified").value(false)).andExpect(jsonPath("$.releaseApproved").value(false)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("MEDICAL-SECRET-DO-NOT-RETURN"));assertFalse(body.contains("missing-reviewer-id"));assertTrue(body.contains("INVALID"));assertTrue(body.contains("CONFIGURED"));
    }
    @Test void requiredDocumentFailureDoesNotBlockLeavingOnboarding()throws Exception{
        mvc.perform(delete("/api/wc/users/me").header("Authorization",token).contentType("application/json").content("{\"confirmation\":\"탈퇴\"}")).andExpect(status().isOk());assertFalse(users.existsById(id));
    }
    @Test void policyUrlValidationRejectsMalformedAndCredentialBearingUrls(){
        for(String v:List.of("","http://example.test","https://","https://user:SECRET@example.test","https://example.test:99999","https://example.test:0"))assertNull(PublishedPolicies.publicHttps(v));
        assertEquals("https://example.test/privacy#health",PublishedPolicies.publicHttps("https://example.test/privacy#health"));
        assertTrue(new PublishedPolicies("https://example.test/terms","https://example.test/privacy","").requiredAvailable());
    }

    @Test void anonymousDraftDocumentsAreReadableButDoNotAuthorizeConsent()throws Exception{
        for(String document:List.of("terms","privacy")) {
            String body=mvc.perform(get("/api/wc/policy-previews/"+document))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Referrer-Policy","no-referrer"))
                .andExpect(header().string("X-Robots-Tag","noindex, nofollow"))
                .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("form-action 'none'")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(body.contains("draft-2026-11-01.1"));assertTrue(body.contains("가입 동의에 사용하는 확정 문서가 아닙니다."));
            assertFalse(body.contains("<script"));assertFalse(body.contains("<form"));assertFalse(body.contains("MEDICAL-SECRET"));
        }
        mvc.perform(get("/api/wc/auth/consent-policy")).andExpect(jsonPath("$.requiredAvailable").value(false))
            .andExpect(jsonPath("$.previews.terms").value("/api/wc/policy-previews/terms"))
            .andExpect(jsonPath("$.previews.healthData").value("/api/wc/policy-previews/privacy#health-data"));
        assertFalse(users.findById(id).orElseThrow().hasRequiredConsents());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consent_events WHERE user_id=?",Long.class,id));
    }
    @Test void previewRoutingDoesNotExposeArbitraryResources()throws Exception{
        mvc.perform(get("/api/wc/policy-previews/application.yml")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wc/policy-previews/terms")).andExpect(status().isUnauthorized());
    }
    @Test void draftUrlsCannotBeConfiguredAsPublishedDocuments(){
        assertNull(PublishedPolicies.publicHttps("https://example.test/api/wc/policy-previews/terms"));
        assertNull(PublishedPolicies.publicHttps("https://example.test/api/wc/policy-previews/privacy#health-data"));
        assertFalse(new PublishedPolicies("https://example.test/api/wc/policy-previews/terms","https://example.test/privacy","").requiredAvailable());
        assertTrue(new PublishedPolicies("https://example.test/terms","https://example.test/privacy","").requiredAvailable());
    }
}
