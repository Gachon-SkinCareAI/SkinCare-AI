package skincare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 애플리케이션 통합 테스트 클래스
@SpringBootTest(properties={
 "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.url=jdbc:h2:mem:context-local;MODE=MySQL",
 "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "jwt.secret=01234567890123456789012345678901"
})
class BackendApplicationTests {

    // Spring 컨텍스트 정상 로딩 여부 확인
    @Test
    void contextLoads() {
    }

}
