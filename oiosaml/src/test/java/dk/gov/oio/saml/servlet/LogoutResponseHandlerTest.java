package dk.gov.oio.saml.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.StatusCode;
import org.w3c.dom.Element;

import dk.gov.oio.saml.util.IdpUtil;
import dk.gov.oio.saml.util.TestConstants;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.shibboleth.shared.codec.Base64Support;
import net.shibboleth.shared.xml.SerializeSupport;

public class LogoutResponseHandlerTest {

    @DisplayName("Test that a valid logout response from IdP")
    @Test
    public void testValidLogoutResponse() throws Exception {
        // Create LogoutRequest
        String nameID = "https://data.gov.dk/model/core/eid/person/uuid/37a5a1aa-67ce-4f70-b7c0-b8e678d585f7";
        LogoutRequest logoutRequest = IdpUtil.createLogoutRequest(nameID, NameID.PERSISTENT, TestConstants.IDP_LOGOUT_REQUEST_URL);

        //Create logoutResponse, marshall deflate and encode
        MessageContext messageContext = IdpUtil.createMessageWithLogoutResponse(logoutRequest, TestConstants.SP_LOGOUT_RESPONSE_URL, StatusCode.SUCCESS);
        Element marshalledMessage = XMLObjectSupport.marshall((XMLObject) messageContext.getMessage());
        final String messageStr = SerializeSupport.nodeToString(marshalledMessage);

        // Deflate
        final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        final DeflaterOutputStream deflaterStream = new DeflaterOutputStream(bytesOut, new Deflater(8, true));
        deflaterStream.write(messageStr.getBytes("UTF-8"));
        deflaterStream.finish();

        String encodedMessage = Base64Support.encode(bytesOut.toByteArray(), Base64Support.UNCHUNKED);

        // Mock HttpServletRequest
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(request.getSession()).thenReturn(session); // Mocked Session
        Mockito.when(request.getRequestURL()).thenReturn(new StringBuffer(TestConstants.SP_ASSERTION_CONSUMER_URL)); // URL
        Mockito.when(request.getMethod()).thenReturn("GET"); // Method: GET
        Mockito.when(request.getParameter("RelayState")).thenReturn(null); // No RelayState
        Mockito.when(request.getParameter("SAMLResponse")).thenReturn(encodedMessage);

        // Mock HttpServletResponse
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(response.getOutputStream()).thenReturn(new DummyOutputStream());

        // Test
        LogoutResponseHandler logoutResponseHandler = new LogoutResponseHandler();
        logoutResponseHandler.handleGet(request, response);

        Mockito.verify(response).sendRedirect("/");
    }

    @DisplayName("Test that an invalid logout response from IdP")
    @Test
    public void testInvalidLogoutResponse() throws Exception {
        // Create LogoutRequest
        String nameID = "https://data.gov.dk/model/core/eid/person/uuid/37a5a1aa-67ce-4f70-b7c0-b8e678d585f7";
        LogoutRequest logoutRequest = IdpUtil.createLogoutRequest(nameID, NameID.PERSISTENT, TestConstants.IDP_LOGOUT_REQUEST_URL);

        //Create logoutResponse, marshall deflate and encode
        MessageContext messageContext = IdpUtil.createMessageWithLogoutResponse(logoutRequest, TestConstants.SP_LOGOUT_RESPONSE_URL, StatusCode.RESPONDER);
        Element marshalledMessage = XMLObjectSupport.marshall((XMLObject) messageContext.getMessage());
        final String messageStr = SerializeSupport.nodeToString(marshalledMessage);

        // Deflate
        final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        final DeflaterOutputStream deflaterStream = new DeflaterOutputStream(bytesOut, new Deflater(8, true));
        deflaterStream.write(messageStr.getBytes("UTF-8"));
        deflaterStream.finish();

        String encodedMessage = Base64Support.encode(bytesOut.toByteArray(), Base64Support.UNCHUNKED);

        // Mock HttpServletRequest
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(request.getSession()).thenReturn(session); // Mocked Session
        Mockito.when(request.getRequestURL()).thenReturn(new StringBuffer(TestConstants.SP_ASSERTION_CONSUMER_URL)); // URL
        Mockito.when(request.getMethod()).thenReturn("GET"); // Method: GET
        Mockito.when(request.getParameter("RelayState")).thenReturn(null); // No RelayState
        Mockito.when(request.getParameter("SAMLResponse")).thenReturn(encodedMessage);

        // Mock HttpServletResponse
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(response.getOutputStream()).thenReturn(new DummyOutputStream());

        // Test
        LogoutResponseHandler logoutResponseHandler = new LogoutResponseHandler();
        logoutResponseHandler.handleGet(request, response);

        Mockito.verify(response).sendRedirect(String.format("/%s/%s",TestConstants.SP_ROUTING_BASE,TestConstants.SP_ROUTING_ERROR));
    }

    private class DummyOutputStream extends ServletOutputStream {
        @Override
        public void write(int i) throws IOException {
            //Do nothing
        }

        @Override
        public boolean isReady() {
            throw new UnsupportedOperationException("Unimplemented method 'isReady'");
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException("Unimplemented method 'setWriteListener'");
        }
    }
}
