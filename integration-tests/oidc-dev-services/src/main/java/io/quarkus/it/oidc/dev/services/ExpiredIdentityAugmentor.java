package io.quarkus.it.oidc.dev.services;

import java.time.Duration;
import java.util.Map;

import jakarta.inject.Singleton;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

@Singleton
public final class ExpiredIdentityAugmentor implements SecurityIdentityAugmentor {

    private volatile int invocationCount = 0;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context,
            Map<String, Object> attributes) {
        if (shouldNotAugment(attributes)) {
            return Uni.createFrom().item(identity);
        }
        return Uni
                .createFrom()
                .item(QuarkusSecurityIdentity
                        .builder(identity)
                        .addAttribute("quarkus.identity.expire-time", expireIn2Seconds())
                        .build());
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity securityIdentity,
            AuthenticationRequestContext authenticationRequestContext) {
        throw new IllegalStateException();
    }

    private boolean shouldNotAugment(Map<String, Object> attributes) {
        RoutingContext routingContext = HttpSecurityUtils.getRoutingContextAttribute(attributes);
        if (routingContext == null) {
            return true;
        }
        if (!routingContext.normalizedPath().contains("/expired-updated-identity")) {
            return true;
        }
        invocationCount++;
        boolean firstInvocation = invocationCount == 1;
        return firstInvocation;
    }

    private static long expireIn2Seconds() {
        return Duration.ofMillis(System.currentTimeMillis())
                .plusSeconds(2)
                .toSeconds();
    }
}
