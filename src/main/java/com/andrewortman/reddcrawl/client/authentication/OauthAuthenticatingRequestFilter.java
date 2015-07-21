package com.andrewortman.reddcrawl.client.authentication;

import com.andrewortman.reddcrawl.client.UserAgentClientRequestFilter;
import com.fasterxml.jackson.databind.JsonNode;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;

public class OauthAuthenticatingRequestFilter implements AuthenticatingRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OauthAuthenticatingRequestFilter.class);

    @Nonnull
    public final AuthenticationState authenticationState = new AuthenticationState();

    @Nonnull
    public final WebTarget authenticationTarget;

    public OauthAuthenticatingRequestFilter(@Nonnull final OauthOptions oauthOptions,
                                            @Nonnull final String userAgent) {

        this.authenticationTarget = ClientBuilder.newClient()
                .register(HttpAuthenticationFeature.basic(oauthOptions.getClientId(), oauthOptions.getClientSecret()))
                .register(new UserAgentClientRequestFilter(userAgent))
                .target(oauthOptions.getAuthenticationEndpoint())
                .path("/api/v1/access_token");

        LOGGER.info("Performing initial authentication request..");
        refreshAuthenticationToken(oauthOptions.getUsername(), oauthOptions.getPassword());

        //spawn a thread that periodically updates the authentication token
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    final boolean shouldReauthenicate;
                    synchronized (authenticationState) {
                        shouldReauthenicate = authenticationState.shouldReauthenticate();
                    }

                    if (shouldReauthenicate) {
                        LOGGER.info("Performing reauthentication with reddit..");
                        try {
                            refreshAuthenticationToken(oauthOptions.getUsername(), oauthOptions.getPassword());
                        } catch (Exception e) {
                            LOGGER.error("Received authentication error during reauthentication", e);
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();

        LOGGER.info("Oauth authentication request filter now ready");
    }

    public void refreshAuthenticationToken(final String accountUser, final String accountPassword) {
        LOGGER.info("Making client authentication request");

        final Form authenticationFormData = new Form()
                .param("grant_type", "password")
                .param("username", accountUser)
                .param("password", accountPassword);

        final JsonNode response = this.authenticationTarget
                .request(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(authenticationFormData), JsonNode.class);

        final String newToken = response.get("access_token").asText();

        synchronized (this.authenticationState) {
            this.authenticationState.updateAccessToken(newToken);
            LOGGER.info("New client request token: " + newToken);
        }
    }

    @Override
    public void filter(final ClientRequestContext requestContext) {
        final String authToken;
        synchronized (this.authenticationState) {
            authToken = this.authenticationState.getCurrentAccessToken();
        }

        LOGGER.debug("Injecting authentication header with bearer token " + authToken);
        requestContext.getHeaders().add("Authorization", "bearer " + authToken);
    }

    //used to maintain authentication state and for locking whenever authentication is taking place (so two auths dont happen at once)
    private class AuthenticationState {
        @Nullable
        private DateTime nextAuthenticationDeadline;

        @Nullable
        private String currentAccessToken;

        public void updateAccessToken(@Nonnull final String newAccessToken) {
            this.currentAccessToken = newAccessToken;
            this.nextAuthenticationDeadline = DateTime.now().plusMinutes(30); //attempt every 30 minutes
        }

        @Nullable
        public String getCurrentAccessToken() {
            return currentAccessToken;
        }

        public boolean shouldReauthenticate() {
            return nextAuthenticationDeadline == null || currentAccessToken == null || nextAuthenticationDeadline.isBefore(DateTime.now());
        }
    }

    public static class OauthOptions {
        @Nonnull
        private final String authenticationEndpont;

        @Nonnull
        private final String clientId;

        @Nonnull
        private final String clientSecret;

        @Nonnull
        private final String username;

        @Nonnull
        private final String password;

        public OauthOptions(@Nonnull final String authenticationEndpont,
                            @Nonnull final String clientId,
                            @Nonnull final String clientSecret,
                            @Nonnull final String username,
                            @Nonnull final String password) {
            this.authenticationEndpont = authenticationEndpont;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.username = username;
            this.password = password;
        }

        @Nonnull
        public String getAuthenticationEndpoint() {
            return authenticationEndpont;
        }

        @Nonnull
        public String getClientId() {
            return clientId;
        }

        @Nonnull
        public String getClientSecret() {
            return clientSecret;
        }

        @Nonnull
        public String getUsername() {
            return username;
        }

        @Nonnull
        public String getPassword() {
            return password;
        }
    }
}
