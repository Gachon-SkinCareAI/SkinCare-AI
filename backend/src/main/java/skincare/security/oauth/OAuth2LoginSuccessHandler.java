package skincare.security.oauth;

import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final SocialFlowStore flows;
    @Override public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication) throws IOException {
        var flow = (SocialFlowStore.Flow)request.getAttribute(SocialAuthorizationRequests.FLOW);
        String fragment;
        try { fragment = "oauth_code="+flows.issue(((CustomOAuth2User)authentication.getPrincipal()).getUser(),flow); }
        catch (RuntimeException e) { fragment = "oauth_error=failed"; }
        redirect(request,response,flow == null ? flows.fallbackRedirect() : flow.redirectUri(),fragment);
    }
    @Override public void onAuthenticationFailure(HttpServletRequest request,HttpServletResponse response,AuthenticationException exception) throws IOException {
        var flow = (SocialFlowStore.Flow)request.getAttribute(SocialAuthorizationRequests.FLOW);
        redirect(request,response,flow == null ? flows.fallbackRedirect() : flow.redirectUri(),"oauth_error=failed");
    }
    private void redirect(HttpServletRequest request,HttpServletResponse response,String uri,String fragment) throws IOException {
        var session = request.getSession(false); if (session != null) session.invalidate();
        SecurityContextHolder.clearContext(); response.setHeader("Cache-Control","no-store");
        response.setHeader("Referrer-Policy","no-referrer"); response.sendRedirect(uri+"#"+fragment);
    }
}
