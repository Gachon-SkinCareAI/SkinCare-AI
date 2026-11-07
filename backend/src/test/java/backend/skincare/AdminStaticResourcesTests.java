package backend.skincare;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminStaticResourcesTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setupMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void loginCanLoadAllPackagedModulesAndStylesWithoutAuthentication() throws Exception {
        var paths = new ArrayList<>(List.of("index.html", "app.js", "style.css"));
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:static/admin/src/*.js")) {
            paths.add("src/" + resource.getFilename());
        }
        assertTrue(paths.containsAll(List.of("src/api.js", "src/state.js", "src/inquiries.js", "src/hospitals.js",
                "src/operations.js", "src/reviews.js", "src/audit.js", "src/ui.js", "src/dashboard.js", "src/constants.js")));
        String html=new ClassPathResource("static/admin/index.html").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("id=\"login-form\""));
        assertTrue(html.contains("./app.js"));
        assertTrue(html.contains("type=\"module\""));
        String script = new ClassPathResource("static/admin/app.js").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(script.contains("/api/wc/admin/auth/login"));
        assertFalse(script.contains("createSeed"));
        for (String path : paths) {
            byte[] expected = new ClassPathResource("static/admin/" + path).getContentAsByteArray();
            var response = mvc.perform(get("/admin/" + path)).andExpect(status().isOk())
                    .andExpect(content().bytes(expected)).andReturn().getResponse();
            String contentType = response.getContentType();
            if (path.endsWith(".js")) assertTrue(contentType.contains("javascript"), path);
            if (path.endsWith(".css")) assertTrue(contentType.contains("text/css"), path);
        }
    }

    @Test void publicAssetsDoNotGrantAdministratorApiAccess() throws Exception {
        mvc.perform(get("/api/wc/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wc/admin/dashboard").with(user("synthetic-user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test void developmentFilesAreNotPackagedAsPublicResources() {
        for (String path : List.of("package.json", "scripts/check.mjs", "src/types/api.d.ts")) {
            assertFalse(new ClassPathResource("static/admin/" + path).exists(), path);
        }
    }
}
