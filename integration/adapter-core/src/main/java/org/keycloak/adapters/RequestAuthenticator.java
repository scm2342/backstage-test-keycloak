package org.keycloak.adapters;

import org.jboss.logging.Logger;
import org.keycloak.KeycloakPrincipal;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class RequestAuthenticator {
    protected Logger log = Logger.getLogger(RequestAuthenticator.class);

    protected HttpFacade facade;
    protected KeycloakDeployment deployment;
    protected AuthChallenge challenge;
    protected int sslRedirectPort;

    public RequestAuthenticator(HttpFacade facade, KeycloakDeployment deployment, int sslRedirectPort) {
        this.facade = facade;
        this.deployment = deployment;
        this.sslRedirectPort = sslRedirectPort;
    }

    public RequestAuthenticator(HttpFacade facade, KeycloakDeployment deployment) {
        this.facade = facade;
        this.deployment = deployment;
    }

    public AuthChallenge getChallenge() {
        return challenge;
    }

    public AuthOutcome authenticate() {
        log.info("--> authenticate()");
        BearerTokenRequestAuthenticator bearer = createBearerTokenAuthenticator();
        AuthOutcome outcome = bearer.authenticate(facade);
        if (outcome == AuthOutcome.FAILED) {
            challenge = bearer.getChallenge();
            return AuthOutcome.FAILED;
        } else if (outcome == AuthOutcome.AUTHENTICATED) {
            completeAuthentication(bearer);
            return AuthOutcome.AUTHENTICATED;
        } else if (deployment.isBearerOnly()) {
            challenge = bearer.getChallenge();
            return AuthOutcome.NOT_ATTEMPTED;
        }

        if (isCached()) {
            return AuthOutcome.AUTHENTICATED;
        }

        OAuthRequestAuthenticator oauth = createOAuthAuthenticator();
        outcome = oauth.authenticate();
        if (outcome == AuthOutcome.FAILED) {
            challenge = oauth.getChallenge();
            return AuthOutcome.FAILED;
        } else if (outcome == AuthOutcome.NOT_ATTEMPTED) {
            challenge = oauth.getChallenge();
            return AuthOutcome.NOT_ATTEMPTED;

        }
        completeAuthentication(oauth);

        // redirect to strip out access code and state query parameters
        facade.getResponse().setHeader("Location", oauth.getStrippedOauthParametersRequestUri());
        facade.getResponse().setStatus(302);
        facade.getResponse().end();

        log.info("AUTHENTICATED");
        return AuthOutcome.AUTHENTICATED;
    }

    protected abstract OAuthRequestAuthenticator createOAuthAuthenticator();

    protected BearerTokenRequestAuthenticator createBearerTokenAuthenticator() {
        return new BearerTokenRequestAuthenticator(deployment);
    }

    protected void completeAuthentication(OAuthRequestAuthenticator oauth) {
        final KeycloakPrincipal principal = new KeycloakPrincipal(oauth.getToken().getSubject(), null);
        RefreshableKeycloakSecurityContext session = new RefreshableKeycloakSecurityContext(deployment, oauth.getTokenString(), oauth.getToken(), oauth.getIdTokenString(), oauth.getIdToken(), oauth.getRefreshToken());
        completeOAuthAuthentication(principal, session);
    }

    protected abstract void completeOAuthAuthentication(KeycloakPrincipal principal, RefreshableKeycloakSecurityContext session);
    protected abstract void completeBearerAuthentication(KeycloakPrincipal principal, RefreshableKeycloakSecurityContext session);
    protected abstract boolean isCached();

    protected void completeAuthentication(BearerTokenRequestAuthenticator bearer) {
        final KeycloakPrincipal principal = new KeycloakPrincipal(bearer.getToken().getSubject(), bearer.getSurrogate());
        RefreshableKeycloakSecurityContext session = new RefreshableKeycloakSecurityContext(deployment, bearer.getTokenString(), bearer.getToken(), null, null, null);
        completeBearerAuthentication(principal, session);
    }

}
