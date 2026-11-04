package backend.skincare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import skincare.SkincareApplication;

@SpringBootTest(classes = SkincareApplication.class, properties = {
    "skincare.consent.terms-policy-url=https://example.test/terms","skincare.consent.privacy-policy-url=https://example.test/privacy",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:skincare;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "spring.mail.username=test", "spring.mail.password=test",
    "jwt.secret=01234567890123456789012345678901"
})
class BackendApplicationTests {
    @Test void contextLoads() {}
}
