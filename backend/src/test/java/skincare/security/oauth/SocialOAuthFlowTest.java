package skincare.security.oauth;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import skincare.SkincareApplication;
import skincare.domain.user.entity.*;
import skincare.domain.user.entity.ENUM.*;
import skincare.repository.UserRepository;
import skincare.security.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes={SkincareApplication.class,SocialOAuthFlowTest.MockProviders.class},properties={
    "skincare.consent.terms-policy-url=https://example.test/terms","skincare.consent.privacy-policy-url=https://example.test/privacy",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.url=jdbc:h2:mem:social;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false",
    "spring.mail.username=test","spring.mail.password=test","jwt.secret=01234567890123456789012345678901",
    "social.redirect-uris=http://localhost:8081/,skincareai://oauth/callback"
})
class SocialOAuthFlowTest {
    static final HttpServer server;
    static volatile String subject="original";
    static final AtomicInteger naverState=new AtomicInteger();
    static {
        try {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath(),id=path.split("/")[1],body;
                if(path.endsWith("/token")){
                    String form=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                    if(!form.contains("client_secret=test-secret") || !form.contains("code=valid-code")) {exchange.sendResponseHeaders(400,-1);exchange.close();return;}
                    if(id.equals("naver")){if(!form.contains("state=")){exchange.sendResponseHeaders(400,-1);exchange.close();return;}naverState.incrementAndGet();}
                    body="{\"access_token\":\"test-access\",\"token_type\":\"Bearer\",\"expires_in\":300}";
                }else{
                    if(!"Bearer test-access".equals(exchange.getRequestHeaders().getFirst("Authorization"))){exchange.sendResponseHeaders(401,-1);exchange.close();return;}
                    body=switch(id){
                        case "google" -> "{\"sub\":\""+subject+"\",\"name\":\"Google QA\",\"email\":\"same@example.test\",\"email_verified\":true}";
                        case "kakao" -> "{\"id\":123456789,\"kakao_account\":{\"profile\":{\"nickname\":\"Kakao QA\"}}}";
                        default -> "{\"resultcode\":\"00\",\"response\":{\"id\":\""+subject+"\",\"nickname\":\"Naver QA\",\"email\":\"same@example.test\"}}";
                    };
                }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @TestConfiguration static class MockProviders {
        @Bean @Primary ClientRegistrationRepository mockRegistrations(){
            var registrations=new ArrayList<ClientRegistration>();String base="http://127.0.0.1:"+server.getAddress().getPort();
            for(String id:List.of("google","kakao","naver"))registrations.add(ClientRegistration.withRegistrationId(id).clientId("test-client").clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/"+id).authorizationUri(base+"/"+id+"/authorize").tokenUri(base+"/"+id+"/token")
                .userInfoUri(base+"/"+id+"/userinfo").userNameAttributeName(id.equals("google")?"sub":id.equals("kakao")?"id":"response").clientName(id).build());
            return new InMemoryClientRegistrationRepository(registrations);
        }
    }
    @Autowired WebApplicationContext context; @Autowired UserRepository users; @Autowired JwtTokenProvider jwt;
    @Autowired SocialAccountService accounts; @Autowired ObjectMapper json;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();subject=UUID.randomUUID().toString();}
    @AfterAll static void stop(){server.stop(0);}
    Map<String,Object> body(MvcResult result)throws Exception{return json.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8),Map.class);}
    record Started(MockHttpSession session,String state,String verifier){}
    Started start(String id)throws Exception{
        String verifier="original-verifier-"+"v".repeat(50);
        var started=mvc.perform(post("/api/wc/auth/social/start").contentType("application/json").content(json.writeValueAsString(Map.of("provider",id,"redirectUri","http://localhost:8081/","codeChallenge",SocialFlowStore.challenge(verifier)))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String path=(String)body(started).get("authorizationPath");
        var redirect=mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andReturn();
        String query=URI.create(redirect.getResponse().getRedirectedUrl()).getRawQuery();
        String state=Arrays.stream(query.split("&")).filter(v->v.startsWith("state=")).map(v->URLDecoder.decode(v.substring(6),StandardCharsets.UTF_8)).findFirst().orElseThrow();
        return new Started((MockHttpSession)redirect.getRequest().getSession(false),state,verifier);
    }
    Map<String,Object> login(String id)throws Exception{
        var started=start(id);
        var callback=mvc.perform(get("/login/oauth2/code/"+id).session(started.session()).param("code","valid-code").param("state",started.state())).andExpect(status().is3xxRedirection()).andReturn();
        String url=callback.getResponse().getRedirectedUrl();assertTrue(url.startsWith("http://localhost:8081/#oauth_code="),url);assertFalse(url.contains("token="));assertTrue(started.session().isInvalid());
        String code=URI.create(url).getFragment().substring("oauth_code=".length());
        var exchange=Map.of("code",code,"codeVerifier",started.verifier());
        var response=mvc.perform(post("/api/wc/auth/social/exchange").contentType("application/json").content(json.writeValueAsString(exchange))).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/wc/auth/social/exchange").contentType("application/json").content(json.writeValueAsString(exchange))).andExpect(status().isBadRequest());
        return body(response);
    }
    @Test void allThreeProvidersRegisterThenRequireExplicitConsentAndPreserveIdentity()throws Exception{
        var ids=new HashSet<String>();
        for(String provider:List.of("google","kakao","naver")){
            var result=login(provider);String id=(String)result.get("userId");ids.add(id);String token="Bearer "+result.get("token");
            assertEquals(true,result.get("consentRequired"));
            mvc.perform(get("/api/wc/analyses").header("Authorization",token)).andExpect(status().isForbidden());
            mvc.perform(put("/api/wc/users/me/consents").header("Authorization",token).contentType("application/json")
                .content("{\"agreedTerms\":true,\"agreedPrivacy\":true,\"agreedHealthData\":true,\"healthDataVersion\":\"2026-11-03\",\"termsVersion\":\"2026-11-03\",\"privacyVersion\":\"2026-11-03\"}")).andExpect(status().isOk());
            mvc.perform(get("/api/wc/users/me").header("Authorization",token)).andExpect(status().isOk());
            var again=login(provider);assertEquals(id,again.get("userId"));assertEquals(false,again.get("consentRequired"));
            var user=users.findById(id).orElseThrow();assertEquals(Provider.valueOf(provider.toUpperCase()),user.getProvider());assertNull(user.getPassword());
            if(provider.equals("kakao"))assertNull(user.getEmail());
            mvc.perform(post("/api/wc/auth/logout").header("Authorization",token).contentType("application/json").content("{}")).andExpect(status().isOk());
            mvc.perform(get("/api/wc/users/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        }
        assertEquals(3,ids.size());assertTrue(naverState.get()>=2);
    }
    @Test void wrongStateCancelAndAnonymousCookieCannotAccessApi()throws Exception{
        var started=start("google");long count=users.count();
        mvc.perform(get("/api/wc/users/me").session(started.session())).andExpect(status().isUnauthorized());
        var wrong=mvc.perform(get("/login/oauth2/code/google").session(started.session()).param("code","valid-code").param("state","wrong"))
            .andExpect(status().is3xxRedirection()).andReturn();assertTrue(wrong.getResponse().getRedirectedUrl().contains("oauth_error="));assertEquals(count,users.count());
        started=start("naver");var cancelled=mvc.perform(get("/login/oauth2/code/naver").session(started.session()).param("error","access_denied").param("state",started.state())).andReturn();
        assertTrue(cancelled.getResponse().getRedirectedUrl().contains("oauth_error="));assertEquals(count,users.count());
    }
    @Test void legacyGoogleKeepsIdAndEmailDoesNotLinkLocalAccounts()throws Exception{
        subject="123456789012345678901";
        var existing=users.saveAndFlush(User.builder().userId("google_"+subject).provider(Provider.GOOGLE).email("before@example.test").nickname("보존할 닉네임").build());
        var result=login("google");assertEquals(existing.getUserId(),result.get("userId"));assertEquals("보존할 닉네임",users.findById(existing.getUserId()).orElseThrow().getNickname());
        var local=users.saveAndFlush(User.builder().userId("local-preserved").email("same@example.test").nickname("기존 사용자").password("existing-hash").build());
        subject=UUID.randomUUID().toString();assertNotEquals(local.getUserId(),login("google").get("userId"));assertEquals("existing-hash",users.findById(local.getUserId()).orElseThrow().getPassword());
        String old=jwt.createToken(local.getUserId(),"USER",local.currentTokenVersion());
        mvc.perform(get("/api/wc/users/me").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        for(String path:List.of("login","signup","email/send","email/verify","password/reset","password/confirm"))
            mvc.perform(post("/api/wc/auth/"+path).contentType("application/json").content("{}")).andExpect(status().isGone());
    }
    @Test void simultaneousFirstLoginCreatesOnlyOneIdentity()throws Exception{
        var identity=SocialIdentity.from("google",Map.of("sub",UUID.randomUUID().toString(),"name","Concurrent QA"));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<String> task=()->{ready.countDown();start.await();return accounts.authenticate(identity).getUserId();};
            var a=pool.submit(task);var b=pool.submit(task);assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            assertEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
    }
    @Test void apiDoesNotTrustAnAuthenticatedBrowserSession()throws Exception{
        var session=new MockHttpSession();var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("cookie-user",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        mvc.perform(get("/api/wc/users/me").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void deletedSocialAccountCannotComeBackAsNewAccount()throws Exception{
        var result=login("google");var user=users.findById((String)result.get("userId")).orElseThrow();user.withdraw();users.saveAndFlush(user);
        long count=users.count();var started=start("google");
        var response=mvc.perform(get("/login/oauth2/code/google").session(started.session()).param("code","valid-code").param("state",started.state())).andReturn();
        assertTrue(response.getResponse().getRedirectedUrl().contains("oauth_error="));assertEquals(count,users.count());
    }
}
