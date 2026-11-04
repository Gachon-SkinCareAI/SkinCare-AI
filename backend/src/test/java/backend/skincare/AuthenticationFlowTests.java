package backend.skincare;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import skincare.repository.UserRepository;
import skincare.domain.user.entity.User;
import skincare.security.JwtTokenProvider;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationFlowTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void onboardingConsentThenLogoutRevokesPersistedToken() throws Exception {
        var user = users.save(User.builder().provider(skincare.domain.user.entity.ENUM.Provider.GOOGLE).userId("flow-user").email("flow@example.test").nickname("flow").build());
        String bearer = "Bearer " + jwt.createToken(user.getUserId(), "USER", user.currentTokenVersion());
        mvc.perform(put("/api/wc/users/me").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"changed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/wc/users/me/consents").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agreedTerms\":true,\"agreedPrivacy\":true,\"agreedHealthData\":true,\"healthDataVersion\":\"2026-11-03\",\"termsVersion\":\"2026-11-03\",\"privacyVersion\":\"2026-11-03\"}"))
                .andExpect(status().isOk());
        assertTrue(users.findById("flow-user").orElseThrow().hasRequiredConsents());
        mvc.perform(put("/api/wc/users/me").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"changed\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/wc/auth/logout").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/wc/users/me").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void malformedBearerReceives401() throws Exception {
        mvc.perform(put("/api/wc/users/me").header("Authorization", "Bearer not-a-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @org.junit.jupiter.api.Test void unavailableSocialProviderDoesNotFallbackToLocalLogin() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/wc/auth/social/providers"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].enabled").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[1].enabled").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[2].enabled").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/wc/auth/social/start").contentType("application/json")
            .content("{\"provider\":\"google\",\"redirectUri\":\"http://localhost:8081/\",\"codeChallenge\":\"not-used\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable());
    }
}
