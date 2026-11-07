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
import skincare.repository.UserRepository;
import skincare.security.JwtTokenProvider;
import skincare.service.PublishedPolicies;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:publication;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "skincare.consent.builtin-policies=true","social.public-base-url=http://127.0.0.1:9800"})
class PolicyPublicationTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;@Autowired UserRepository users;@Autowired JwtTokenProvider jwt;@Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void actualDocumentsAreAnonymousVersionedAndAvailable()throws Exception{
        mvc.perform(get("/api/wc/policies/2026-11-01.2/privacy")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("현재 위치를 SkinCare 서버로 보내지 않습니다.")));
        mvc.perform(get("/api/wc/auth/consent-policy")).andExpect(status().isOk())
            .andExpect(jsonPath("$.requiredAvailable").value(true))
            .andExpect(jsonPath("$.terms.version").value("2026-11-03"))
            .andExpect(jsonPath("$.healthData.url").value("http://127.0.0.1:9800/api/wc/policies/2026-11-03/privacy#health-data"));
        for(String page:List.of("terms","privacy")) {
            String body=mvc.perform(get("/api/wc/policies/2026-11-03/"+page)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            for(String expected:List.of("장재완","wanjang0320@gachon.ac.kr","최대 5년","2026-11-03"))assertTrue(body.contains(expected));
            assertFalse(body.contains("<script"));assertFalse(body.contains("가입 동의에 사용하는 확정 문서가 아닙니다."));
        }
        mvc.perform(get("/api/wc/policies/2026-11-03/application.yml")).andExpect(status().isUnauthorized());
    }
    @Test void previousVersionRequiresExplicitNewConsent()throws Exception{
        String id=UUID.randomUUID().toString();var u=User.builder().userId(id).provider(Provider.GOOGLE).socialSubjectHash(UUID.randomUUID().toString()).nickname("QA").email(id+"@example.test").build();
        u.acceptConsents("2026-10-29","2026-10-29");u.acceptHealthConsent("2026-11-01");users.saveAndFlush(u);
        String token="Bearer "+jwt.createToken(id,"USER",u.currentTokenVersion());
        mvc.perform(get("/api/wc/analyses").header("Authorization",token)).andExpect(status().isForbidden());
        String payload="{\"agreedTerms\":true,\"agreedPrivacy\":true,\"agreedHealthData\":true,\"termsVersion\":\"2026-11-03\",\"privacyVersion\":\"2026-11-03\",\"healthDataVersion\":\"2026-11-03\"}";
        mvc.perform(put("/api/wc/users/me/consents").header("Authorization",token).contentType("application/json").content(payload.replace("2026-11-03","2026-10-29"))).andExpect(status().isBadRequest());
        assertFalse(users.findById(id).orElseThrow().hasRequiredConsents());
        mvc.perform(put("/api/wc/users/me/consents").header("Authorization",token).contentType("application/json").content(payload)).andExpect(status().isOk());
        assertTrue(users.findById(id).orElseThrow().hasRequiredConsents());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM consent_events WHERE user_id=?",Long.class,id));
        mvc.perform(get("/api/wc/analyses").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void builtinModeRestrictsInsecureOriginsAndPublishesOptionalConsentWithoutExport(){
        for(String base:List.of("http://external.test","http://user:password@localhost","https://api.test/path","https://api.test?key=secret","https://api.test:99999"))assertNull(PublishedPolicies.policyOrigin(base));
        assertEquals("https://api.test",PublishedPolicies.policyOrigin("https://api.test/"));
        var policy=new PublishedPolicies("","","",true,"http://127.0.0.1:9800");assertTrue(policy.requiredAvailable());assertEquals("http://127.0.0.1:9800/api/wc/policies/2026-11-04/training",policy.trainingUrl());assertFalse(policy.manifest().trainingExportEnabled());
        assertFalse(new PublishedPolicies("","","",false,"http://127.0.0.1:9800").requiredAvailable());
    }
}
