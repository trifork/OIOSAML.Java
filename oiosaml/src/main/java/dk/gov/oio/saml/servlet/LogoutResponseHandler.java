package dk.gov.oio.saml.servlet;

import java.io.IOException;

import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.opensaml.saml.saml2.core.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import dk.gov.oio.saml.config.Configuration;
import dk.gov.oio.saml.service.OIOSAML3Service;
import dk.gov.oio.saml.servlet.ErrorHandler.ERROR_TYPE;
import dk.gov.oio.saml.util.ExternalException;
import dk.gov.oio.saml.util.InternalException;
import dk.gov.oio.saml.util.SamlHelper;
import dk.gov.oio.saml.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LogoutResponseHandler extends SAMLHandler {
    private static final Logger log = LoggerFactory.getLogger(LogoutResponseHandler.class);

    @Override
    public void handleGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ExternalException, InternalException {
        MessageContext context = decodeGet(httpServletRequest);

        handle(httpServletRequest, httpServletResponse, context);
    }

    @Override
    public void handlePost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ExternalException, InternalException, IOException {
        MessageContext context = decodePost(httpServletRequest);

        handle(httpServletRequest, httpServletResponse, context);
    }
    
    private void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, MessageContext context) throws IOException, ExternalException, InternalException {
        LogoutResponse logoutResponse = getSamlObject(context, LogoutResponse.class);

        String statusCode = null;
        String statusMessage = null;
        if (logoutResponse.getStatus() != null) {
            if (logoutResponse.getStatus().getStatusCode() != null) {
                statusCode = logoutResponse.getStatus().getStatusCode().getValue();
            }

            if (logoutResponse.getStatus().getStatusMessage() != null) {
                statusMessage = logoutResponse.getStatus().getStatusMessage().getValue();
            }
        }

        // Log response
        try {
            Element element = SamlHelper.marshallObject(logoutResponse);
            log.debug("LogoutResponse: {}", StringUtil.elementToString(element));
        } catch (MarshallingException e) {
            log.warn("Could not marshall LogoutResponse for logging purposes");
        }
        log.info("Incoming LogoutResponse - ID:'{}' InResponseTo:'{}' Issuer:'{}' Status:'{} {}' IssueInstant:'{}' Destination:'{}'",
                logoutResponse.getID(),
                logoutResponse.getInResponseTo(),
                logoutResponse.getIssuer() != null ?
                        logoutResponse.getIssuer().getValue() : "",
                statusCode,
                statusMessage,
                logoutResponse.getIssueInstant() != null ?
                        logoutResponse.getIssueInstant().toString() : "",
                logoutResponse.getDestination());

        // Check if it was a success
        if (StatusCode.SUCCESS.equals(statusCode)) {
            Configuration config = OIOSAML3Service.getConfig();            
            String url = StringUtil.getUrl(httpServletRequest, config.getLogoutPage());

            httpServletResponse.sendRedirect(url);
        }
        else {
            ErrorHandler.handle(httpServletRequest, httpServletResponse, ERROR_TYPE.LOGOUT_ERROR, "Logout failed - response from IdP: " + statusCode + " / " + statusMessage);

            return;
        }
    }
}
