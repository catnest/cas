package org.apereo.cas.support.oauth.validator.token;

import org.apereo.cas.audit.AuditableExecution;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.oauth.OAuth20Constants;
import org.apereo.cas.support.oauth.OAuth20GrantTypes;
import org.apereo.cas.support.oauth.services.OAuthRegisteredService;
import org.apereo.cas.support.oauth.util.OAuth20Utils;
import org.apereo.cas.util.Pac4jUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.springframework.core.Ordered;

import java.util.Optional;

/**
 * This is {@link BaseOAuth20TokenRequestValidator}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@Slf4j
@RequiredArgsConstructor
@Getter
@Setter
public abstract class BaseOAuth20TokenRequestValidator implements OAuth20TokenRequestValidator {
    /**
     * Access strategy enforcer.
     */
    protected final AuditableExecution registeredServiceAccessStrategyEnforcer;

    /**
     * Service manager instance, managing the registry.
     */
    protected final ServicesManager servicesManager;

    /**
     * Service factory instance.
     */
    protected final ServiceFactory<WebApplicationService> webApplicationServiceServiceFactory;

    private int order = Ordered.LOWEST_PRECEDENCE;

    /**
     * Check the grant type against expected grant types.
     *
     * @param type          the current grant type
     * @param expectedTypes the expected grant types
     * @return whether the grant type is supported
     */
    private static boolean isGrantTypeSupported(final String type, final OAuth20GrantTypes... expectedTypes) {
        LOGGER.debug("Grant type received: [{}]", type);
        for (val expectedType : expectedTypes) {
            if (OAuth20Utils.isGrantType(type, expectedType)) {
                return true;
            }
        }
        LOGGER.error("Unsupported grant type: [{}]", type);
        return false;
    }

    /**
     * Is grant type supported.
     *
     * @param registeredService the registered service
     * @param type              the type
     * @return the boolean
     */
    protected boolean isGrantTypeSupportedBy(final OAuthRegisteredService registeredService, final OAuth20GrantTypes type) {
        return OAuth20Utils.isAuthorizedGrantTypeForService(type.getType(), registeredService);
    }

    /**
     * Is grant type supported service.
     *
     * @param registeredService the registered service
     * @param type              the type
     * @return true/false
     */
    protected boolean isGrantTypeSupportedBy(final OAuthRegisteredService registeredService, final String type) {
        if (registeredService == null) {
            LOGGER.warn("No registered service definition was supplied to examine for supported grant types");
            return false;
        }

        val grantTypes = registeredService.getSupportedGrantTypes();
        if (grantTypes == null || grantTypes.isEmpty()) {
            LOGGER.warn("Service definition [{}] does not define any authorized/supported grant types. "
                + "It is STRONGLY recommended that you authorize and assign grant types to the service definition. "
                + "This behavior will be enforced by CAS in future versions.", registeredService.getServiceId());
            return true;
        }
        val supported = grantTypes.stream().anyMatch(t -> t.equalsIgnoreCase(type));
        if (!supported) {
            LOGGER.warn("Unauthorized requested grant type. None of the grant types [{}] defined by "
                + "service definition [{}] match the requested grant type [{}]", grantTypes, registeredService.getServiceId(), type);
            return false;
        }
        return true;
    }

    @Override
    public boolean validate(final J2EContext context) {
        val request = context.getRequest();
        val response = context.getResponse();

        val grantType = request.getParameter(OAuth20Constants.GRANT_TYPE);
        if (!isGrantTypeSupported(grantType, OAuth20GrantTypes.values())) {
            LOGGER.warn("Grant type is not supported: [{}]", grantType);
            return false;
        }

        val manager = Pac4jUtils.getPac4jProfileManager(request, response);
        val profile = (Optional<CommonProfile>) manager.get(true);
        if (profile == null || !profile.isPresent()) {
            LOGGER.warn("Could not locate authenticated profile for this request. Request is not authenticated");
            return false;
        }

        val uProfile = profile.get();
        if (uProfile == null) {
            LOGGER.warn("Could not locate authenticated profile for this request as null");
            return false;
        }

        return validateInternal(context, grantType, manager, uProfile);
    }

    /**
     * Validate internal.
     *
     * @param context     the context
     * @param grantType   the grant type
     * @param manager     the manager
     * @param userProfile the profile
     * @return true/false
     */
    protected boolean validateInternal(final J2EContext context, final String grantType, final ProfileManager manager,
                                       final UserProfile userProfile) {
        return false;
    }

    /**
     * Gets grant type.
     *
     * @return the grant type
     */
    protected abstract OAuth20GrantTypes getGrantType();

    @Override
    public boolean supports(final J2EContext context) {
        val grantType = context.getRequestParameter(OAuth20Constants.GRANT_TYPE);
        return OAuth20Utils.isGrantType(grantType, getGrantType());
    }
}
