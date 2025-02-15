package dk.gov.oio.saml.servlet;

import java.io.IOException;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.shibboleth.shared.component.ComponentInitializationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.velocity.app.VelocityEngine;
import org.opensaml.core.config.InitializationException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPPostDecoder;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPSOAP11Decoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;

import dk.gov.oio.saml.util.ExternalException;
import dk.gov.oio.saml.util.InternalException;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPSOAP11Encoder;

public abstract class SAMLHandler {
    private static final Logger log = LoggerFactory.getLogger(SAMLHandler.class);

    public abstract void handleGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ExternalException, InternalException, InitializationException;
    public abstract void handlePost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ExternalException, InternalException, IOException;
    public void handleSOAP(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ExternalException, InternalException, IOException {
        httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    MessageContext decodeGet(HttpServletRequest httpServletRequest) throws InternalException, ExternalException {
        try {
            log.debug("Decoding message as HTTPRedirect");

            HTTPRedirectDeflateDecoder decoder = new HTTPRedirectDeflateDecoder();
            decoder.setHttpServletRequestSupplier(() -> httpServletRequest);

            decoder.initialize();
            decoder.decode();
            return decoder.getMessageContext();
        }
        catch (ComponentInitializationException e) {
            throw new InternalException("Could not initialize decoder", e);
        }
        catch (MessageDecodingException e) {
            throw new ExternalException("Could not decode request", e);
        }
    }

    MessageContext decodePost(HttpServletRequest httpServletRequest) throws InternalException, ExternalException {
        try {
            log.debug("Decoding message as HTTP Post");

            HTTPPostDecoder decoder = new HTTPPostDecoder();
            decoder.setHttpServletRequestSupplier(() -> httpServletRequest);

            decoder.initialize();
            decoder.decode();
            return decoder.getMessageContext();
        }
        catch (ComponentInitializationException e) {
            throw new InternalException("Could not initialize decoder", e);
        }
        catch (MessageDecodingException e) {
            throw new ExternalException("Could not decode request", e);
        }
    }

    MessageContext decodeSOAP(HttpServletRequest httpServletRequest) throws InternalException, ExternalException {
        try {
            log.debug("Decoding message as HTTP SOAP11");

            HTTPSOAP11Decoder decoder = new HTTPSOAP11Decoder();
            decoder.setHttpServletRequestSupplier(() -> httpServletRequest);

            decoder.initialize();
            decoder.decode();
            return decoder.getMessageContext();
        }
        catch (ComponentInitializationException e) {
            throw new InternalException("Could not initialize decoder", e);
        }
        catch (MessageDecodingException e) {
            throw new ExternalException("Could not decode request", e);
        }
    }

    void sendGet(HttpServletResponse httpServletResponse, MessageContext message) throws ComponentInitializationException, MessageEncodingException {
        log.debug("Encoding, deflating and sending message (HTTPRedirect)");

        HTTPRedirectDeflateEncoder encoder = new HTTPRedirectDeflateEncoder();

        encoder.setHttpServletResponseSupplier(() -> httpServletResponse);
        encoder.setMessageContext(message);

        encoder.initialize();
        encoder.encode();
    }

    void sendPost(HttpServletResponse httpServletResponse, MessageContext message) throws ComponentInitializationException, MessageEncodingException {
        log.debug("Encoding and sending message (HTTPPost)");

        HTTPPostEncoder encoder = new HTTPPostEncoder();

        encoder.setHttpServletResponseSupplier(() -> httpServletResponse);
        encoder.setMessageContext(message);

        VelocityEngine velocityEngine = new VelocityEngine();

        // Set properties for ClasspathResourceLoader
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "classpath");
        properties.setProperty("classpath.resource.loader.class", 
                               "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        // Initialize Velocity with the properties
        velocityEngine.init(properties);
        
        encoder.setVelocityEngine(velocityEngine);

        encoder.initialize();
        encoder.encode();
    }

    void sendSOAP(HttpServletResponse httpServletResponse, MessageContext message) throws ComponentInitializationException, MessageEncodingException {
        log.debug("Encoding and sending message (SOAP)");

        HTTPSOAP11Encoder encoder = new HTTPSOAP11Encoder();

        encoder.setHttpServletResponseSupplier(() -> httpServletResponse);
        encoder.setMessageContext(message);

        encoder.initialize();
        encoder.prepareContext();
        encoder.encode();
    }

    <T> T getSamlObject(MessageContext context, Class<T> clazz) throws ExternalException {
        SAMLObject samlObject = (SAMLObject) context.getMessage();
        if (samlObject == null) {
            throw new ExternalException("Saml message was null");
        }

        try {
            return clazz.cast(samlObject);
        } catch (ClassCastException e) {
            throw new ExternalException("Saml message was of the wrong type", e);
        }
    }
}
