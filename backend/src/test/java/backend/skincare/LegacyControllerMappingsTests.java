package backend.skincare;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties = {
        "skincare.legacy.controllers-enabled=true",
        "spring.datasource.url=jdbc:h2:mem:legacy-controller-mappings;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class LegacyControllerMappingsTests extends BackendApplicationTests {
    @Autowired RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void canonicalAndLegacyRoutesAreBothRegistered() {
        assertMapping("/api/wc/hospitals", "HospitalController");
        assertMapping("/api/wc/legacy/hospitals", "HospitalUserController");
        assertMapping("/api/wc/inquiries", "InquiryController");
        assertMapping("/api/wc/legacy/inquiries", "UserInquiryController");
    }

    private void assertMapping(String path, String controller) {
        var controllers = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getPatternValues().contains(path))
                .map(entry -> entry.getValue().getBeanType().getSimpleName())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(controller), controllers);
    }
}
