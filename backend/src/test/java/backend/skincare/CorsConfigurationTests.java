package backend.skincare;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import skincare.domain.admin.entity.Admin;
import skincare.repository.AdminRepository;
import skincare.security.JwtTokenProvider;

import java.util.List;

/**
 * 관리자 웹(React 개발 서버·운영용 화면)이 브라우저에서 API를 부를 수 있는지 검증한다.
 *
 * <p>CORS는 {@code SecurityConfig}의 전역 설정이 정한다. 허용 출처는 localhost와 127.0.0.1 둘 다여야 하고(둘은 서로 다른 출처다),
 * 관리자 화면이 쓰는 모든 메서드(GET·POST·PUT·PATCH·DELETE)가 허용되어야 한다. 어긋나면 브라우저에서만 403이 나고 서버
 * 직접 호출(curl)로는 재현되지 않는다.
 */
class CorsConfigurationTests extends BackendApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired AdminRepository admins;
    @Autowired JwtTokenProvider jwt;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    String admin;

    /** 관리자 웹이 열리는 주소: React 개발 서버(5173)와 앱 웹(8081)을 localhost·127.0.0.1 양쪽으로. */
    static final List<String> ALLOWED = List.of("http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:8081", "http://127.0.0.1:8081");

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var a =
                admins.findById("cors-admin")
                        .orElseGet(() -> Admin.builder().adminId("cors-admin").name("CORS 검증 관리자").password(encoder.encode("ServiceTest123!")).build());
        admins.saveAndFlush(a);
        admin = "Bearer " + jwt.createToken(a.getAdminId(), "ADMIN", a.currentTokenVersion());
    }

    MockHttpServletResponse preflight(String origin, String method, String path) throws Exception {
        return mvc.perform(options(path).header("Origin", origin).header("Access-Control-Request-Method", method).header("Access-Control-Request-Headers", "authorization,content-type")).andReturn().getResponse();
    }

    /** 브라우저가 보내는 실제 요청(프리플라이트 통과 후): Origin 헤더를 붙인 인증된 요청. */
    MockHttpServletResponse browserRequest(String origin, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Origin", origin).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    @Test
    void everyAdminWebOriginMayCallTheApiInBothHostSpellings() throws Exception {
        for (String origin : ALLOWED) {
            var response = preflight(origin, "POST", "/api/wc/admin/auth/login");
            assertEquals(200, response.getStatus(), origin);
            assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"), origin);
        }
    }

    @Test
    void unknownOriginsAreRejected() throws Exception {
        for (String origin : List.of("https://evil.example", "http://localhost:5174", "http://127.0.0.1:3000", "null")) {
            var response = preflight(origin, "POST", "/api/wc/admin/auth/login");
            assertEquals(403, response.getStatus(), origin);
            assertNull(response.getHeader("Access-Control-Allow-Origin"), origin);
        }
        // 실제 요청도 막는다 (관리자 토큰이 있어도 허용되지 않은 출처의 브라우저 요청은 거부)
        assertEquals(403, browserRequest("https://evil.example", get("/api/wc/admin/hospitals")).getStatus());
    }

    @Test
    void everyMethodTheAdminWebUsesIsAllowedIncludingPatch() throws Exception {
        for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173")) {
            for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
                var response = preflight(origin, method, "/api/wc/admin/users/x/status");
                assertEquals(200, response.getStatus(), method + " from " + origin);
                assertTrue(String.valueOf(response.getHeader("Access-Control-Allow-Methods")).contains(method), method + ": " + response.getHeader("Access-Control-Allow-Methods"));
            }
        }
    }

    @Test
    void realBrowserRequestsFromTheAdminWebReachTheBusinessLogic() throws Exception {
        // 사용자·진단 상태 변경(PATCH), 문의 답변(PUT), 기관 동기화·등록(POST)이 CORS(403)가 아니라 업무 규칙에서 응답해야 한다
        for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173")) {
            var patch = browserRequest(origin, patch("/api/wc/admin/users/no-such-user/status").content("{\"status\":\"ACTIVE\"}"));
            assertEquals(404, patch.getStatus(), "PATCH from " + origin + " → 없는 사용자는 404여야 한다(CORS 403이면 안 됨)");
            var put = browserRequest(origin, put("/api/wc/admin/inquiries/11111111-2222-4333-8444-555555555555/answer").content("{}"));
            assertNotEquals(403, put.getStatus(), "PUT from " + origin);
            var diagnosis = browserRequest(origin, patch("/api/wc/admin/diagnoses/no-such/status").content("{\"status\":\"COMPLETED\"}"));
            assertNotEquals(403, diagnosis.getStatus(), "PATCH 진단 from " + origin);
            var get = browserRequest(origin, get("/api/wc/admin/hospitals"));
            assertEquals(200, get.getStatus(), "GET from " + origin);
            assertEquals(origin, get.getHeader("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void sameHostRequestsWithoutAnOriginHeaderAreUnaffected() throws Exception {
        assertEquals(200, mvc.perform(get("/api/wc/admin/hospitals").header("Authorization", admin)).andReturn().getResponse().getStatus());
    }
}
