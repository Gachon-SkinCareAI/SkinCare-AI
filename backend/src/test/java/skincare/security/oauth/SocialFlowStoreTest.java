package skincare.security.oauth;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import skincare.domain.user.entity.User;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.junit.jupiter.api.Assertions.*;
class SocialFlowStoreTest {
    static class MutableClock extends Clock {
        long time=1000; public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return Instant.ofEpochMilli(time);}
    }
    @Test void oneUseCodesRequireTheOriginalVerifierAndExpire() {
        var clock=new MutableClock();var store=new SocialFlowStore("http://localhost:8081/",clock);
        String verifier="v".repeat(64),challenge=SocialFlowStore.challenge(verifier);
        var user=User.builder().userId("user").build();
        var flow=store.takeFlow(store.start("google","http://localhost:8081/",challenge),"google");
        var code=store.issue(user,flow);
        assertThrows(ResponseStatusException.class,()->store.exchange(code,"x".repeat(64)));
        assertEquals("user",store.exchange(code,verifier).userId());
        assertThrows(ResponseStatusException.class,()->store.exchange(code,verifier));
        String expired=store.issue(user,flow);clock.time+=60_000;
        assertThrows(ResponseStatusException.class,()->store.exchange(expired,verifier));
        clock.time+=600_000;assertThrows(ResponseStatusException.class,()->store.issue(user,flow));
    }
    @Test void redirectsAndProviderFlowBindingAreStrict() {
        var store=new SocialFlowStore("http://localhost:8081/");String challenge=SocialFlowStore.challenge("a".repeat(64));
        assertThrows(ResponseStatusException.class,()->store.start("google","https://attacker.test/",challenge));
        assertThrows(ResponseStatusException.class,()->store.start("google","http://localhost:8081/?next=evil",challenge));
        assertThrows(ResponseStatusException.class,()->store.start("google","http://localhost:8081/","short"));
        var flow=store.start("google","http://localhost:8081/",challenge);
        assertThrows(ResponseStatusException.class,()->store.takeFlow(flow,"naver"));
        assertThrows(ResponseStatusException.class,()->store.takeFlow(flow,"google"));
    }
    @Test void providerSubjectsAreRequiredAndEmailIsOptional() {
        var kakao=SocialIdentity.from("kakao",Map.of("id",123L));assertNull(kakao.email());assertEquals("사용자",kakao.nickname());
        var naver=SocialIdentity.from("naver",Map.of("resultcode","00","response",Map.of("id","SubjectCase")));assertNull(naver.email());
        var lower=SocialIdentity.from("naver",Map.of("resultcode","00","response",Map.of("id","subjectcase")));assertNotEquals(naver.hash(),lower.hash());
        assertThrows(OAuth2AuthenticationException.class,()->SocialIdentity.from("google",Map.of("email","x@example.test")));
        assertThrows(OAuth2AuthenticationException.class,()->SocialIdentity.from("naver",Map.of("resultcode","error","response",Map.of("id","id"))));
        assertNull(SocialIdentity.from("google",Map.of("sub","id","email","x@example.test","email_verified",false)).email());
        assertThrows(OAuth2AuthenticationException.class,()->SocialIdentity.from("local",Map.of("sub","id")));
    }
}
