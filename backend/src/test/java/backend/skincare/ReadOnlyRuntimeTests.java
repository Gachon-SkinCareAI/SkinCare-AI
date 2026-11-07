package backend.skincare;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import skincare.service.FileStorageService;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.FileAppender;
import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyRuntimeTests extends BackendApplicationTests {
    @TempDir Path temporary;

    @Test void defaultRuntimeDoesNotAttachFileLoggers() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (var logger : context.getLoggerList()) {
            logger.iteratorForAppenders().forEachRemaining(appender ->
                assertFalse(appender instanceof FileAppender, logger.getName()));
        }
    }

    @Test void unusedLegacyStorageDoesNotWriteAtStartupButExplicitUploadsStillWork() throws Exception {
        var storage = new FileStorageService();
        Path root = temporary.resolve("not-created-on-startup");
        ReflectionTestUtils.setField(storage, "uploadDir", root.toString());
        ReflectionTestUtils.invokeMethod(storage, "init");
        assertFalse(Files.exists(root));
        byte[] fixture = new byte[]{1,2,3};
        String key = storage.storeImage(new MockMultipartFile("file", "fixture.jpg", "image/jpeg", fixture), "test");
        assertArrayEquals(fixture, Files.readAllBytes(root.resolve(key)));
    }
}
