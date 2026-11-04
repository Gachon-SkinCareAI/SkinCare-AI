package skincare.security.oauth;

import java.net.URI;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.LinkedMultiValueMap;

@Configuration
public class SocialOAuthConfiguration {
    /** Allows startup when no providers are configured. */
    static final class Registrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {
        final Map<String,ClientRegistration> values;
        Registrations(Map<String,ClientRegistration> values) { this.values = Map.copyOf(values); }
        @Override public ClientRegistration findByRegistrationId(String id) { return id == null ? null : values.get(id); }
        @Override public Iterator<ClientRegistration> iterator() { return values.values().iterator(); }
    }
    @Bean public ClientRegistrationRepository socialRegistrations(Environment env) {
        String base = env.getRequiredProperty("social.public-base-url").replaceAll("/+$","");
        URI uri = URI.create(base);
        if (!("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && Set.of("localhost","127.0.0.1").contains(uri.getHost())))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("SOCIAL_PUBLIC_BASE_URL must be HTTPS (or loopback HTTP)");
        Map<String,ClientRegistration> registrations = new LinkedHashMap<>();
        for (String id : List.of("google","kakao","naver")) {
            String client = env.getProperty("social."+id+".client-id","").strip(), secret = env.getProperty("social."+id+".client-secret","").strip();
            if (!configured(client) || !configured(secret)) continue;
            var builder = ClientRegistration.withRegistrationId(id).clientId(client).clientSecret(secret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(base+"/login/oauth2/code/"+id).clientName(id);
            switch (id) {
                case "google" -> builder.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo").userNameAttributeName("sub").scope("profile","email");
                case "kakao" -> builder.authorizationUri("https://kauth.kakao.com/oauth/authorize").tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me").userNameAttributeName("id").scope("profile_nickname");
                case "naver" -> builder.authorizationUri("https://nid.naver.com/oauth2.0/authorize").tokenUri("https://nid.naver.com/oauth2.0/token")
                    .userInfoUri("https://openapi.naver.com/v1/nid/me").userNameAttributeName("response");
            }
            registrations.put(id,builder.build());
        }
        return new Registrations(registrations);
    }
    private boolean configured(String value) { return !value.isBlank() && !value.startsWith("local-") && !value.startsWith("YOUR_"); }
    @Bean @Order(1) public SecurityFilterChain socialFilterChain(HttpSecurity http,ClientRegistrationRepository registrations,
            SocialFlowStore flows,CustomOAuth2UserService users,OAuth2LoginSuccessHandler handler) throws Exception {
        var requests = new SocialAuthorizationRequests(registrations,flows);
        var tokenClient = new RestClientAuthorizationCodeTokenResponseClient();
        // Naver requires OAuth state in the authorization-code token request too.
        tokenClient.addParametersConverter(grant -> {
            var params = new LinkedMultiValueMap<String,String>();
            if ("naver".equals(grant.getClientRegistration().getRegistrationId())) params.set("state",grant.getAuthorizationExchange().getAuthorizationRequest().getState());
            return params;
        });
        OAuth2AuthorizedClientService transientClients = new OAuth2AuthorizedClientService() {
            @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id,String principal) { return null; }
            @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client,Authentication principal) { /* Discard provider access/refresh tokens after userinfo. */ }
            @Override public void removeAuthorizedClient(String id,String principal) {}
        };
        http.securityMatcher("/oauth2/**","/login/oauth2/**")
            .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .oauth2Login(o -> o.clientRegistrationRepository(registrations).authorizedClientService(transientClients)
                .authorizationEndpoint(a -> a.authorizationRequestResolver(requests).authorizationRequestRepository(requests))
                .tokenEndpoint(t -> t.accessTokenResponseClient(tokenClient))
                .userInfoEndpoint(u -> u.userService(users)).successHandler(handler).failureHandler(handler));
        return http.build();
    }
}
