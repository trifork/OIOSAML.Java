package dk.gov.oio.saml.servlet;

import java.io.IOException;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.SessionIndex;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import dk.gov.oio.saml.config.Configuration;
import dk.gov.oio.saml.service.IdPMetadataService;
import dk.gov.oio.saml.service.LogoutRequestService;
import dk.gov.oio.saml.service.LogoutResponseService;
import dk.gov.oio.saml.service.OIOSAML3Service;
import dk.gov.oio.saml.session.AssertionWrapper;
import dk.gov.oio.saml.session.LogoutRequestWrapper;
import dk.gov.oio.saml.session.SessionHandler;
import dk.gov.oio.saml.util.AuditRequestUtil;
import dk.gov.oio.saml.util.ExternalException;
import dk.gov.oio.saml.util.InternalException;
import dk.gov.oio.saml.util.SamlHelper;
import dk.gov.oio.saml.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.shibboleth.shared.component.ComponentInitializationException;

public class LogoutRequestHandler extends SAMLHandler {
    private static final Logger log = LoggerFactory.getLogger(LogoutRequestHandler.class);

    @Override
    public void handleGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ExternalException, InternalException, IOException {
        log.debug("Handling HTTP LogoutRequest");
        if (isServiceProviderRequest(httpServletRequest)) {
            handleServiceProviderRequest( httpServletRequest,  httpServletResponse);
            return;
        }

        // IdP Initiated, generate response
        MessageContext context = decodeGet(httpServletRequest);
        LogoutRequest logoutRequest = getSamlObject(context, LogoutRequest.class);

        MessageContext outgoingMessage = handleRequest(httpServletRequest, new LogoutRequestWrapper(logoutRequest));
        try {
            sendPost(httpServletResponse, outgoingMessage);
        } catch (ComponentInitializationException | MessageEncodingException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public void handlePost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ExternalException, InternalException, IOException {
        handleGet(httpServletRequest, httpServletResponse);
    }

    @Override
    public void handleSOAP(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ExternalException, InternalException {
        log.debug("Handling SOAP LogoutRequest");
        // IdP Initiated, generate response
        MessageContext context = decodeSOAP(httpServletRequest);
        LogoutRequest logoutRequest = getSamlObject(context, LogoutRequest.class);

        MessageContext outgoingMessage = handleRequest(httpServletRequest, new LogoutRequestWrapper(logoutRequest));
        try {
            sendSOAP(httpServletResponse, outgoingMessage);
        } catch (ComponentInitializationException | MessageEncodingException e) {
            throw new InternalException(e);
        }
    }

    private void handleServiceProviderRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ExternalException, InternalException {
        log.debug("Handling ServiceProvider LogoutRequest");
        SessionHandler sessionHandler = OIOSAML3Service.getSessionHandlerFactory().getHandler();

        // SP initiated, generate logoutRequest and send to IdP

        boolean authenticated = sessionHandler.isAuthenticated(httpServletRequest.getSession());

        log.debug("Authenticated: {}", authenticated);

        if (!authenticated) {
            // if not logged in, just forward to front-page
            Configuration config = OIOSAML3Service.getConfig();
            String url = StringUtil.getUrl(httpServletRequest, config.getLogoutPage());

            // Invalidate current http session - remove all data
            httpServletRequest.getSession().invalidate();

            log.warn("User not logged in, redirecting to " + url);
            httpServletResponse.sendRedirect(url);
            return;
        }

        AssertionWrapper assertion = sessionHandler.getAssertion(httpServletRequest.getSession());
        String sessionId = sessionHandler.getSessionId(httpServletRequest.getSession());

        OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                .createBasicAuditBuilder(httpServletRequest, "SLO1", "ServiceProviderLogout")
                .withAuthnAttribute("SP_SESSION_ID", sessionId)
                .withAuthnAttribute("ASSERTION_ID", (null != assertion)? assertion.getID():"")
                .withAuthnAttribute("REQUEST", "VALID"));

        // Invalidate users session
        sessionHandler.logout(httpServletRequest.getSession(), assertion);

        // Invalidate current http session - remove all data
        httpServletRequest.getSession().invalidate();

        // Send LogoutRequest to IdP only if the session actually has an authenticated user on it
        log.debug("Send LogoutRequest to IdP");
        try {
            String entityID = assertion.getIssuer();
            SingleLogoutService logoutEndpoint = IdPMetadataService.getInstance().getLogoutEndpoint(entityID);

            if (logoutEndpoint == null) {
                // In connection with unsolicited saml assertions, there might not be an SLO endpoint
                // Just forward to front-page
                Configuration config = OIOSAML3Service.getConfig();
                String url = StringUtil.getUrl(httpServletRequest, config.getLogoutPage());

                // Invalidate current http session - remove all data
                httpServletRequest.getSession().invalidate();

                log.info("No SLO endpoint, redirecting to " + url);
                httpServletResponse.sendRedirect(url);
                return;
            }

            String location = logoutEndpoint.getLocation();

            MessageContext messageContext = LogoutRequestService.createMessageWithLogoutRequest(assertion.getSubjectNameId(), assertion.getSubjectNameIdFormat(), location, assertion.getSessionIndex());
            LogoutRequestWrapper logoutRequest = new LogoutRequestWrapper(getSamlObject(messageContext, LogoutRequest.class));

            OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                    .createBasicAuditBuilder(httpServletRequest, "SLO2", "OutgoingLogoutRequest")
                    .withAuthnAttribute("SP_SESSION_ID", sessionId)
                    .withAuthnAttribute("LOGOUT_REQUEST_ID", logoutRequest.getID())
                    .withAuthnAttribute("LOGOUT_REQUEST_DESTINATION", logoutRequest.getDestination()));

            // Log LogoutRequest
            try {
                Element element = SamlHelper.marshallObject(logoutRequest.getLogoutRequest());
                log.debug("LogoutRequest: {}", StringUtil.elementToString(element));
            } catch (MarshallingException e) {
                log.warn("Could not marshall LogoutRequest for logging purposes");
            }
            log.info("Outgoing LogoutRequest - ID:'{}' Issuer:'{}' IssueInstant:'{}' SessionIndexes:'{}' Destination:'{}'",
                    logoutRequest.getID(),
                    logoutRequest.getIssuerAsString(),
                    logoutRequest.getIssueInstantAsString(),
                    logoutRequest.getSessionIndexesAsString(),
                    logoutRequest.getDestination());

            sendGet(httpServletResponse, messageContext);
        }
        catch (InitializationException | ComponentInitializationException | MessageEncodingException e) {
            throw new InternalException(e);
        }
    }

    private MessageContext handleRequest(HttpServletRequest httpServletRequest, LogoutRequestWrapper logoutRequest) throws ExternalException, InternalException {
        log.debug("Handling LogoutRequest");
        SessionHandler sessionHandler = OIOSAML3Service.getSessionHandlerFactory().getHandler();

        // Log LogoutRequest
        try {
            Element element = SamlHelper.marshallObject(logoutRequest.getLogoutRequest());
            log.debug("LogoutRequest: {}", StringUtil.elementToString(element));
        } catch (MarshallingException e) {
            log.warn("Could not marshall LogoutRequest for logging purposes");
        }
        log.info("Incoming LogoutRequest - ID:'{}' Issuer:'{}' IssueInstant:'{}' SessionIndexes:'{}' Destination:'{}'",
                logoutRequest.getID(),
                logoutRequest.getIssuerAsString(),
                logoutRequest.getIssueInstantAsString(),
                logoutRequest.getSessionIndexesAsString(),
                logoutRequest.getDestination());

        sessionHandler.storeLogoutRequest(httpServletRequest.getSession(), logoutRequest);

        OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                .createBasicAuditBuilder(httpServletRequest, "SLO4", "IncomingLogoutRequest")
                .withAuthnAttribute("LOGOUT_REQUEST_ID", logoutRequest.getID())
                .withAuthnAttribute("SIGNATURE_REFERENCE", logoutRequest.getSignatureReferenceID())
                .withAuthnAttribute("LOGOUT_REQUEST_DESTINATION", logoutRequest.getDestination()));

        if (null == logoutRequest.getSessionIndexes() || logoutRequest.getSessionIndexes().isEmpty()) {
            OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                    .createBasicAuditBuilder(httpServletRequest, "SLO4", "InvalidatedSession")
                    .withAuthnAttribute("LOGOUT_REQUEST_ID", logoutRequest.getID())
                    .withAuthnAttribute("SP_SESSION_INDEX", "INVALID")
                    .withAuthnAttribute("REQUEST", "INVALID"));
        } else {
            for (SessionIndex sessionIndex : logoutRequest.getSessionIndexes()) {
                AssertionWrapper assertion = sessionHandler.getAssertion(sessionIndex.getValue());
                if (null == assertion) {
                    OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                            .createBasicAuditBuilder(httpServletRequest, "SLO4", "InvalidatedSession")
                            .withAuthnAttribute("LOGOUT_REQUEST_ID", logoutRequest.getID())
                            .withAuthnAttribute("SP_SESSION_INDEX", sessionIndex.getValue())
                            .withAuthnAttribute("REQUEST", "INVALID"));
                    continue;
                }
                sessionHandler.logout(httpServletRequest.getSession(), assertion);

                OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                        .createBasicAuditBuilder(httpServletRequest, "SLO4", "InvalidatedSession")
                        .withAuthnAttribute("LOGOUT_REQUEST_ID", logoutRequest.getID())
                        .withAuthnAttribute("SP_SESSION_INDEX", sessionIndex.getValue())
                        .withAuthnAttribute("SP_SESSION_ID", sessionHandler.getSessionId(sessionIndex.getValue()))
                        .withAuthnAttribute("ASSERTION_ID", assertion.getID())
                        .withAuthnAttribute("SUBJECT_NAME_ID", assertion.getSubjectNameId())
                        .withAuthnAttribute("REQUEST", "VALID"));
            }
        }

        // Invalidate current http session - remove all data
        httpServletRequest.getSession().invalidate();

        // Create LogoutResponse
        try {
            IdPMetadataService metadataService = IdPMetadataService.getInstance();
            String logoutResponseEndpoint = metadataService.getLogoutResponseEndpoint(logoutRequest.getIssuerAsString()); // Has to be from the specific IdP that verified the user
            MessageContext messageContext = LogoutResponseService.createMessageWithLogoutResponse(logoutRequest, logoutResponseEndpoint);

            // Log LogoutRequest
            try {
                Element element = SamlHelper.marshallObject(logoutRequest.getLogoutRequest());
                log.debug("LogoutRequest: {}", StringUtil.elementToString(element));
            } catch (MarshallingException e) {
                log.warn("Could not marshall LogoutRequest for logging purposes");
            }
            log.info("Outgoing LogoutRequest - ID:'{}' Issuer:'{}' IssueInstant:'{}' SessionIndexes:'{}' Destination:'{}'",
                    logoutRequest.getID(),
                    logoutRequest.getIssuerAsString(),
                    logoutRequest.getIssueInstantAsString(),
                    logoutRequest.getSessionIndexesAsString(),
                    logoutRequest.getDestination());

            return messageContext;
        }
        catch (InitializationException e) {
            throw new InternalException(e);
        }
    }

    private boolean isServiceProviderRequest(HttpServletRequest httpServletRequest) {
        String samlRequest = httpServletRequest.getParameter("SAMLRequest");
        return  samlRequest == null || samlRequest.isEmpty();
    }


}
