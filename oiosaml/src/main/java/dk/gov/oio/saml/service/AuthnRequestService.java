package dk.gov.oio.saml.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.opensaml.core.config.InitializationException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Extensions;
import org.opensaml.saml.saml2.core.IDPEntry;
import org.opensaml.saml.saml2.core.IDPList;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.core.Scoping;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.context.SecurityParametersContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.gov.oio.saml.config.Configuration;
import dk.gov.oio.saml.extensions.appswitch.AppSwitch;
import dk.gov.oio.saml.extensions.appswitch.AppSwitchPlatform;
import dk.gov.oio.saml.extensions.appswitch.Platform;
import dk.gov.oio.saml.extensions.appswitch.ReturnURL;
import dk.gov.oio.saml.model.NSISLevel;
import dk.gov.oio.saml.util.Constants;
import dk.gov.oio.saml.util.ExternalException;
import dk.gov.oio.saml.util.InternalException;
import dk.gov.oio.saml.util.SamlHelper;
import jakarta.servlet.http.HttpServletRequest;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.security.impl.RandomIdentifierGenerationStrategy;
import net.shibboleth.shared.xml.impl.BasicParserPool;

public class AuthnRequestService {
    private static final Logger log = LoggerFactory.getLogger(AuthnRequestService.class);

    // Single instance
    private static AuthnRequestService singleInstance = new AuthnRequestService();

    public static AuthnRequestService getInstance() {
        return singleInstance;
    }

    // Credential service
    public MessageContext getMessageContext(HttpServletRequest request) throws ComponentInitializationException, MessageDecodingException {
        log.debug("Decoding Http Redirect deflate");

        HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();
            decoder.setHttpServletRequestSupplier(() -> request);

            BasicParserPool parserPool = new BasicParserPool();
            parserPool.initialize();

            decoder.setParserPool(parserPool);
            decoder.initialize();
            decoder.decode();

            MessageContext msgContext = decoder.getMessageContext();
            decoder.destroy();

            return msgContext;
    }

    public AuthnRequest getAuthnRequest(HttpServletRequest request) throws ComponentInitializationException, MessageDecodingException {
        MessageContext messageContext = getMessageContext(request);
        return (AuthnRequest) messageContext.getMessage();
    }

    public MessageContext createMessageWithAuthnRequest(boolean isPassive, boolean forceAuthn, NSISLevel requiredNsisLevel, String attributeProfile, AppSwitchPlatform platform, String selectedIdp) throws InternalException, ExternalException, InitializationException {
        // Create message context
        MessageContext messageContext = new MessageContext();

        // Get Destination URL from IdP metadata
        if (selectedIdp == null) {
            selectedIdp = OIOSAML3Service.getConfig().getIdpEntityID();
        }
        String[] idpList = selectedIdp.split(" ");

        log.debug("Selected IdP: {}, IdP scoping: {}", idpList[0], idpList.length > 1 ? idpList[1] : "(none)");

        String destination = getDestination(idpList[0]);
        String scope = idpList.length > 1 ? idpList[1] : null;

        // Create AuthnRequest
        AuthnRequest newAuthnRequest = createAuthnRequest(destination, isPassive, forceAuthn, requiredNsisLevel, attributeProfile, platform, scope);
        messageContext.setMessage(newAuthnRequest);

        // Destination
        SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
        SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);

        SingleSignOnService endpoint = SamlHelper.build(SingleSignOnService.class);
        endpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
        endpoint.setLocation(destination);

        endpointContext.setEndpoint(endpoint);

        // Signing info
        SignatureSigningParameters signatureSigningParameters = new SignatureSigningParameters();
        signatureSigningParameters.setSigningCredential(OIOSAML3Service.getCredentialService().getPrimaryBasicX509Credential());

        // we do not actually use relayState for anything, but some IdP's require it
        SAMLBindingSupport.setRelayState(messageContext, "_" + UUID.randomUUID().toString());
        
        signatureSigningParameters.setSignatureAlgorithm(OIOSAML3Service.getConfig().getSignatureAlgorithm());
        messageContext.getSubcontext(SecurityParametersContext.class, true).setSignatureSigningParameters(signatureSigningParameters);

        return messageContext;
    }

    public AuthnRequest createAuthnRequest(String destination, boolean isPassive, boolean forceAuthn, NSISLevel requiredNsisLevel, AppSwitchPlatform platform) throws InitializationException {
        return createAuthnRequest(destination, isPassive, forceAuthn, requiredNsisLevel, null, platform, null);
    }

    public AuthnRequest createAuthnRequest(String destination, boolean isPassive, boolean forceAuthn, NSISLevel requiredNsisLevel, String attributeProfile, AppSwitchPlatform platform, String scope) throws InitializationException {
        // Create new AuthnRequest
        AuthnRequest authnRequest = SamlHelper.build(AuthnRequest.class);

        if (scope != null) {
            Scoping scoping = SamlHelper.build(Scoping.class);
            IDPList idpList = SamlHelper.build(IDPList.class);
            IDPEntry idpEntry = SamlHelper.build(IDPEntry.class);
            idpEntry.setProviderID(scope);
            idpList.getIDPEntrys().add(idpEntry);
            scoping.setIDPList(idpList);
            authnRequest.setScoping(scoping);
        }

        // Set ID
        RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy();
        String id = secureRandomIdGenerator.generateIdentifier();
        authnRequest.setID(id);

        Configuration config = OIOSAML3Service.getConfig();

        // Set Values
        authnRequest.setDestination(destination);
        authnRequest.setIssueInstant(Instant.now());
        authnRequest.setIsPassive(isPassive);
        authnRequest.setForceAuthn(forceAuthn);
        authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        authnRequest.setAssertionConsumerServiceURL(config.getServletAssertionConsumerURL());

        // Set Issuer
        Issuer issuer = SamlHelper.build(Issuer.class);
        authnRequest.setIssuer(issuer);

        issuer.setValue(config.getSpEntityID());

        List<AuthnContextClassRef> authnContextClassRefs = new ArrayList<>();

        // Set Requested LOA if supplied
        if (requiredNsisLevel != null && requiredNsisLevel != NSISLevel.NONE) {
            AuthnContextClassRef authnContextClassRef = SamlHelper.build(AuthnContextClassRef.class);
            authnContextClassRef.setURI(requiredNsisLevel.getUrl());
            authnContextClassRefs.add(authnContextClassRef);
        }

        // Set Requested AttributeProfile if supplied
        if (Constants.ATTRIBUTE_PROFILE_PERSON.equals(attributeProfile) || Constants.ATTRIBUTE_PROFILE_PROFESSIONAL.equals(attributeProfile)) {
            AuthnContextClassRef authnContextClassRef = SamlHelper.build(AuthnContextClassRef.class);
            authnContextClassRef.setURI(attributeProfile);

            authnContextClassRefs.add(authnContextClassRef);
        }

        // If any AuthnContextClassRefs were created, add them to AuthnRequest
        if (!authnContextClassRefs.isEmpty()) {
            RequestedAuthnContext requestedAuthnContext = SamlHelper.build(RequestedAuthnContext.class);
            // OIO-SP-06
            if(requiredNsisLevel != null && requiredNsisLevel != NSISLevel.NONE) {
                requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.MINIMUM);
            }

            requestedAuthnContext.getAuthnContextClassRefs().addAll(authnContextClassRefs);
            authnRequest.setRequestedAuthnContext(requestedAuthnContext);
        }

        // If platform provided add AppSwitch extension
        if(platform != null) {
            addAppSwitchToExtensions(authnRequest, platform);
        }


        return authnRequest;
    }

    private static void addAppSwitchToExtensions(AuthnRequest authnRequest, AppSwitchPlatform platform) {
        Configuration config = OIOSAML3Service.getConfig();
        ReturnURL returnURLNode = SamlHelper.build(ReturnURL.class);

        String returnUrl = GetReturnURLForPlatform(platform, config);
        returnURLNode.setValue(returnUrl);

        Platform platformNode = SamlHelper.build(Platform.class);
        platformNode.setValue(platform);

        AppSwitch appSwitch = SamlHelper.build(AppSwitch.class);
        appSwitch.setPlatform(platformNode);
        appSwitch.setReturnURL(returnURLNode);

        Extensions extensions = authnRequest.getExtensions();
        if(extensions == null)
            extensions = SamlHelper.build(Extensions.class);

        extensions.getUnknownXMLObjects().add(appSwitch);
        authnRequest.setExtensions(extensions);
    }

    private static String GetReturnURLForPlatform(AppSwitchPlatform platform, Configuration config) {
        String returnURL = null;
        if (platform == AppSwitchPlatform.Android)
            returnURL = config.getAppSwitchReturnURLForAndroid();
        else if(platform == AppSwitchPlatform.iOS)
            returnURL = config.getAppSwitchReturnURLForIOS();

        if(StringUtils.isBlank(returnURL))
            throw new IllegalArgumentException("Missing configuration for '" + Constants.SP_APPSWITCH_RETURNURL_ANDROID +"'");

        return returnURL;
    }

    private String getDestination(String entityID) throws ExternalException, InternalException {
        EntityDescriptor metadata = IdPMetadataService.getInstance().getIdPMetadata(entityID).getEntityDescriptor();
        IDPSSODescriptor idpssoDescriptor = metadata.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);

        for (SingleSignOnService singleSignOnService : idpssoDescriptor.getSingleSignOnServices()) {
            if (SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(singleSignOnService.getBinding())) {
                return singleSignOnService.getLocation();
            }
        }

        throw new ExternalException("Could not find SSO endpoint for Redirect binding in metadata");
    }
}
