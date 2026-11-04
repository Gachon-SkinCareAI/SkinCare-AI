package skincare.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisTimingFilterTest {
    @Test void logsOnlyOperationAndDurationWhilePreservingResponse() throws Exception {
        Logger logger=(Logger)LoggerFactory.getLogger("skincare.performance");
        var appender=new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var request=new MockHttpServletRequest("POST","/api/wc/analyses/private-record/answers");
            request.addHeader("Authorization","Bearer private-token");
            request.setContent("private-symptom-text".getBytes());
            var response=new MockHttpServletResponse();
            new AnalysisTimingFilter().doFilter(request,response,(req,res)->{
                ((jakarta.servlet.http.HttpServletResponse)res).setStatus(201);
                res.getWriter().write("unchanged");
            });
            assertEquals(201,response.getStatus()); assertEquals("unchanged",response.getContentAsString());
            assertEquals(1,appender.list.size());
            String message=appender.list.get(0).getFormattedMessage();
            assertTrue(message.matches("event=analysis_request operation=answer status=201 completed=true elapsed_ms=\\d+"));
            assertFalse(message.contains("private-"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void downstreamFailureIsPreservedAndMarkedIncomplete() {
        Logger logger=(Logger)LoggerFactory.getLogger("skincare.performance");
        var appender=new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var request=new MockHttpServletRequest("POST","/api/wc/analyses");
            var response=new MockHttpServletResponse();
            IOException expected=new IOException("private upstream detail");
            IOException actual=assertThrows(IOException.class,()->new AnalysisTimingFilter().doFilter(request,response,(req,res)->{throw expected;}));
            assertSame(expected,actual);
            assertEquals(1,appender.list.size());
            assertTrue(appender.list.get(0).getFormattedMessage().contains("completed=false"));
            assertFalse(appender.list.get(0).getFormattedMessage().contains("private"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void unrelatedRequestsArePassedThroughWithoutTimingLog() throws Exception {
        Logger logger=(Logger)LoggerFactory.getLogger("skincare.performance");
        var appender=new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var response=new MockHttpServletResponse();
            new AnalysisTimingFilter().doFilter(new MockHttpServletRequest("GET","/api/wc/analyses"),response,
                    (req,res)->res.getWriter().write("ok"));
            assertEquals("ok",response.getContentAsString()); assertTrue(appender.list.isEmpty());
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
