package backend.skincare;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import skincare.external.HttpAiStatusClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AiStatusClientTests {
    HttpServer server;String base;
    AtomicReference<String> response=new AtomicReference<>();AtomicInteger status=new AtomicInteger(200);
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/ai/health",exchange->{
            assertEquals("Bearer fixture-only",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();base="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void onlyAllowlistedOperationalFieldsLeaveTheProxy() {
        response.set("""
          {"status":"ok","models":{"detect":"yolov8s-wound","classify":"C:/private/model.pt"},
           "bank_version":"2026-10-18","card_version":"2026-10-20","llm_configured":false,
           "inflight":2,"max_inflight":4,"token":"DO NOT EXPOSE","patient":"DO NOT EXPOSE"}
          """);
        var result=new HttpAiStatusClient(base,"fixture-only").status();
        assertEquals("READY",result.get("status"));assertFalse(result.containsKey("token"));assertFalse(result.containsKey("patient"));
        assertEquals("unreported",((java.util.Map<?,?>)result.get("reportedModels")).get("classify"));
        assertEquals(2L,result.get("inflight"));assertNotNull(result.get("checkedAt"));
    }
    @Test void degradedUnauthorizedUnavailableAndInvalidResponsesAreDistinct() {
        var client=new HttpAiStatusClient(base,"fixture-only");
        response.set("{\"status\":\"degraded\"}");assertEquals("DEGRADED",client.status().get("status"));
        status.set(401);response.set("{\"secret\":\"must stay upstream\"}");assertEquals("AUTH_ERROR",client.status().get("status"));
        status.set(503);assertEquals("UNAVAILABLE",client.status().get("status"));
        status.set(200);response.set("{}");assertEquals("INVALID_RESPONSE",client.status().get("status"));
        response.set("not json");assertEquals("UNAVAILABLE",client.status().get("status"));
        assertEquals("UNCONFIGURED",new HttpAiStatusClient(base,"").status().get("status"));
        server.stop(0);assertEquals("UNAVAILABLE",client.status().get("status"));
    }
}
