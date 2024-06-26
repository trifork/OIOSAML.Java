package dk.gov.oio.saml.servlet;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import dk.gov.oio.saml.audit.AuditService;
import dk.gov.oio.saml.session.SessionHandler;
import dk.gov.oio.saml.util.*;
import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.*;

import dk.gov.oio.saml.service.AssertionService;
import dk.gov.oio.saml.service.OIOSAML3Service;
import dk.gov.oio.saml.service.validation.AssertionValidationService;
import dk.gov.oio.saml.session.AssertionWrapper;
import dk.gov.oio.saml.session.AuthnRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public class AssertionHandler extends SAMLHandler {
    private static final Logger log = LoggerFactory.getLogger(AssertionHandler.class);

    @Override
    public void handleGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        throw new UnsupportedOperationException("GET not allowed");
    }

    @Override
    public void handlePost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ExternalException, InternalException, IOException {
        HttpSession session = httpServletRequest.getSession();

        // Decode request
        MessageContext<SAMLObject> messageContext = decodePost(httpServletRequest);
        SAMLObject samlObject = messageContext.getMessage();

        // Get response object
        if (!(samlObject instanceof Response)) {
            throw new ExternalException("Saml message was not a response");
        }
        Response response = (Response) samlObject;

        // Log response
        try {
            Element element = SamlHelper.marshallObject(response);
            log.debug("Response: {}", StringUtil.elementToString(element));
        } catch (MarshallingException e) {
            log.warn("Could not marshall Response for logging purposes");
        }

        // Get response status
        Status status = response.getStatus();
        String responseStatus = "";
        String nestedResponseStatus = "";
        if (status != null) {
            StatusCode code = status.getStatusCode();
            if (code != null) {
                responseStatus += code.getValue();
                StatusCode nestedCode = code.getStatusCode();
                if (nestedCode != null) {
                    nestedResponseStatus = nestedCode.getValue();
                }
            }

            StatusMessage message = status.getStatusMessage();
            if (message != null) {
                responseStatus += " " + message.getMessage();
            }
        }

        // Get instant
        DateTime issueInstant = response.getIssueInstant();
        String instant = "";
        if (issueInstant != null) {
            instant = issueInstant.toString();
        }

        // Get issuer
        String issuer = response.getIssuer() != null ? response.getIssuer().getValue() : null;

        // Log response
        log.info("Incoming Response - ID:'{}' InResponseTo:'{}' Issuer:'{}' Status:'{}' IssueInstant:'{}' Destination:'{}'", response.getID(), response.getInResponseTo(), issuer, responseStatus, instant, response.getDestination());

        // Get AuthnRequest with matching ID (inResponseTo)
        SessionHandler sessionHandler = OIOSAML3Service.getSessionHandlerFactory().getHandler();
        AuthnRequestWrapper authnRequest = response.getInResponseTo() != null ? sessionHandler.getAuthnRequest(httpServletRequest.getSession()) : null;

        if (authnRequest == null) {
            // Unsolicited saml response
            if (!OIOSAML3Service.getConfig().isUnsolicitedSAMLResponseAllowed()) {
                throw new InternalException("No AuthnRequest found on session");
            }
        }

        boolean passiveAndNotAuthenticated = false;

        Assertion assertion;
        if (authnRequest != null && authnRequest.isPassive() && StatusCode.RESPONDER.equals(responseStatus) && StatusCode.NO_PASSIVE.equals(nestedResponseStatus)) {
            // We asked for passive login, and IdP found no active login - create "guest" assertion: 
			log.debug("Received passive response, setting passive assertion");
            assertion = SamlHelper.build(Assertion.class);
            assertion.setID("" + System.currentTimeMillis());
            assertion.setIssueInstant(DateTime.now());
            AttributeStatement attributeStatement = SamlHelper.build(AttributeStatement.class);

            Attribute attribute = createAttribute(Constants.ASSURANCE_LEVEL, "0");

            attributeStatement.getAttributes().add(attribute);
            assertion.getAttributeStatements().add(attributeStatement);

            passiveAndNotAuthenticated = true;

        } else {
            // Get assertion
            AssertionService assertionService = new AssertionService();
            assertion = assertionService.getAssertion(response);
        }

        // Audit log builder
        AuditService.Builder auditBuilder = AuditRequestUtil
                .createBasicAuditBuilder(httpServletRequest, "BSA6", "ValidateAssertion")
                .withAuthnAttribute("AUTHN_REQUEST_ID", authnRequest != null ? authnRequest.getId() : "-")
                .withAuthnAttribute("RESPONSE_ID", response.getID())
                .withAuthnAttribute("ASSERTION_ID", assertion.getID())
                .withAuthnAttribute("IN_RESPONSE_TO", response.getInResponseTo())
                .withAuthnAttribute("RESPONSE_STATUS", responseStatus)
                .withAuthnAttribute("ISSUER", issuer)
                .withAuthnAttribute("ISSUE_INSTANT", instant)
                .withAuthnAttribute("DESTINATION", response.getDestination());

        if (!passiveAndNotAuthenticated) {

            // Validate
            AssertionWrapper wrapper;
            try {
                AssertionValidationService validationService = new AssertionValidationService();
                validationService.validate(httpServletRequest, messageContext, response, assertion, authnRequest);
    
                if (assertion.getAttributeStatements() == null || assertion.getAttributeStatements().size() != 1) {
                    throw new ExternalException("Assertion AttributeStatements were null or had more than one");
                }
    
                // Assertion needs to be validated before creating the wrapper
                wrapper = new AssertionWrapper(assertion);
    
                log.debug("Assertion: {}", wrapper);
    
                auditBuilder
                        .withAuthnAttribute("RESULT", "VALID")
                        .withAuthnAttribute("SESSION_INDEX", wrapper.getSessionIndex())
                        .withAuthnAttribute("SIGNATURE_REFERENCE", assertion.getSignatureReferenceID())
                        .withAuthnAttribute("SIGNATURE_ENTITY", wrapper.getSigningCredentialEntityId())
                        .withAuthnAttribute("ASSURANCE_LEVEL", wrapper.getAssuranceLevel())
                        .withAuthnAttribute("NSIS_LEVEL", wrapper.getNsisLevel().getName())
                        .withAuthnAttribute("SUBJECT_NAME_ID", wrapper.getSubjectNameId());
            }
            catch (AssertionValidationException e) {
                log.info("Failed validating assertion: {}",new AssertionWrapper(assertion).toString());
                auditBuilder.withAuthnAttribute("RESULT", e.getMessage());
                throw new ExternalException(e);
            }
            finally {
                OIOSAML3Service.getAuditService().auditLog(auditBuilder);
            }
        }

        AssertionWrapper assertionWrapper = new AssertionWrapper(assertion);
        httpServletRequest.getSession(true);

        session = sessionHandler.storeAssertion(session, assertionWrapper, httpServletRequest);

        OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                .createBasicAuditBuilder(httpServletRequest, "BSA7", "CreateSession")
                .withAuthnAttribute("SP_SESSION_ID", sessionHandler.getSessionId(session))
                .withAuthnAttribute("SP_SESSION_TIMEOUT", String.valueOf(session.getMaxInactiveInterval())));

        // redirect to SESSION_REQUESTED_PATH or to login page if not found
        String url = StringUtil.defaultIfEmpty(authnRequest != null ? authnRequest.getRequestPath() : null,
                StringUtil.getUrl(httpServletRequest, OIOSAML3Service.getConfig().getLoginPage()));

        OIOSAML3Service.getAuditService().auditLog(AuditRequestUtil
                .createBasicAuditBuilder(httpServletRequest, "BSA8", "SendRedirect")
                .withAuthnAttribute("URL_REDIRECT",url));

        httpServletResponse.sendRedirect(url);
    }

    private Attribute createAttribute(String attributeName, String attributeValue) {
        Attribute attribute = SamlHelper.build(Attribute.class);
        attribute.setName(attributeName);

        attribute.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:basic");

        XSAnyBuilder xsAnyBuilder = new XSAnyBuilder();
        XSAny value = xsAnyBuilder.buildObject(SAMLConstants.SAML20_NS, AttributeValue.DEFAULT_ELEMENT_LOCAL_NAME, SAMLConstants.SAML20_PREFIX);

        value.setTextContent(attributeValue);
        attribute.getAttributeValues().add(value);
        return attribute;
    }
}
