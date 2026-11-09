package skincare.security.oauth;

import jakarta.servlet.http.*;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Spring verifies OAuth state; handoff metadata is stored exclusively in its server session. */
final class SocialAuthorizationRequests
        implements OAuth2AuthorizationRequestResolver,
                AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    static final String FLOW = SocialAuthorizationRequests.class.getName() + ".flow";
    private final DefaultOAuth2AuthorizationRequestResolver resolver;
    private final HttpSessionOAuth2AuthorizationRequestRepository repository =
            new HttpSessionOAuth2AuthorizationRequestRepository();
    private final SocialFlowStore flows;

    SocialAuthorizationRequests(ClientRegistrationRepository registrations, SocialFlowStore flows) {
        resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        registrations, "/oauth2/authorization");
        this.flows = flows;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return attach(request, resolver.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String id) {
        return attach(request, resolver.resolve(request, id));
    }

    private OAuth2AuthorizationRequest attach(
            HttpServletRequest request, OAuth2AuthorizationRequest auth) {
        if (auth == null) return null;
        String provider = auth.getAttribute("registration_id");
        var flow = flows.takeFlow(request.getParameter("flow"), provider);
        return OAuth2AuthorizationRequest.from(auth)
                .attributes(attrs -> attrs.put(FLOW, flow))
                .build();
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return repository.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest auth,
            HttpServletRequest request,
            HttpServletResponse response) {
        repository.saveAuthorizationRequest(auth, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        var auth = repository.removeAuthorizationRequest(request, response);
        if (auth != null) request.setAttribute(FLOW, auth.getAttribute(FLOW));
        return auth;
    }
}
