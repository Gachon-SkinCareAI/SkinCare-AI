package backend.skincare;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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

// /admin/     React console (admin-web/dist)
// /admin/ops/ operations console (admin-web/operational): reviews, audit, password change
class AdminStaticResourcesTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setupMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String text(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test void reactConsoleAndItsAssetsLoadWithoutAuthentication() throws Exception {
        String html = text("static/admin/index.html");
        assertTrue(html.contains("id=\"root\""), "React entry point");
        var assets = Pattern.compile("(?:src|href)=\"(/admin/[^\"]+)\"").matcher(html);
        var paths = new ArrayList<String>();
        while (assets.find()) paths.add(assets.group(1));
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("/admin/assets/") && p.endsWith(".js")), "bundled script");
        for (String path : paths) {
            byte[] expected = new ClassPathResource("static" + path).getContentAsByteArray();
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().bytes(expected));
        }
    }

    @Test void reactPagePathsServeTheConsoleOnRefresh() throws Exception {
        for (String page : List.of("/admin", "/admin/", "/admin/dashboard", "/admin/diagnoses", "/admin/monitoring",
                "/admin/facilities", "/admin/users", "/admin/inquiries", "/admin/analyses")) {
            mvc.perform(get(page)).andExpect(status().isOk()).andExpect(forwardedUrl("/admin/index.html"));
        }
    }

    @Test void loginPageImagesLoadWithoutAuthentication() throws Exception {
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:static/admin/*.{png,svg}")) {
            mvc.perform(get("/admin/" + resource.getFilename())).andExpect(status().isOk());
        }
        mvc.perform(get("/admin/favicon.svg")).andExpect(status().isOk());
    }

    @Test void operationsConsoleLoadsAllModulesWithoutAuthentication() throws Exception {
        mvc.perform(get("/admin/ops")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/ops/"));
        mvc.perform(get("/admin/ops/")).andExpect(status().isOk()).andExpect(forwardedUrl("/admin/ops/index.html"));
        var paths = new ArrayList<>(List.of("index.html", "app.js", "style.css"));
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:static/admin/ops/src/*.js")) {
            paths.add("src/" + resource.getFilename());
        }
        assertTrue(paths.containsAll(List.of("src/api.js", "src/state.js", "src/inquiries.js", "src/hospitals.js",
                "src/operations.js", "src/reviews.js", "src/audit.js", "src/ui.js", "src/dashboard.js", "src/constants.js")));
        String html = text("static/admin/ops/index.html");
        assertTrue(html.contains("id=\"login-form\""));
        assertTrue(html.contains("./app.js"));
        assertTrue(html.contains("type=\"module\""));
        String script = text("static/admin/ops/app.js");
        assertTrue(script.contains("/api/wc/admin/auth/login"));
        assertFalse(script.contains("createSeed"));
        for (String path : paths) {
            byte[] expected = new ClassPathResource("static/admin/ops/" + path).getContentAsByteArray();
            var response = mvc.perform(get("/admin/ops/" + path)).andExpect(status().isOk())
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
        // Only the listed console files are public; other /admin paths stay protected.
        mvc.perform(get("/admin/secret.json")).andExpect(status().isUnauthorized());
    }

    @Test void developmentFilesAreNotPackagedAsPublicResources() {
        for (String path : List.of("package.json", "scripts/check.mjs", "src/types/api.d.ts", "src/App.tsx",
                "ops/package.json", "ops/README.md")) {
            assertFalse(new ClassPathResource("static/admin/" + path).exists(), path);
        }
    }
}
