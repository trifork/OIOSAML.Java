package dk.gov.oio.saml.extensions.appswitch;

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.AbstractSAMLObjectMarshaller;
import org.w3c.dom.Element;

import net.shibboleth.shared.xml.ElementSupport;

public class PlatformMarshaller extends AbstractSAMLObjectMarshaller {

    protected void marshallElementContent(final XMLObject samlObject, final Element domElement)
            throws MarshallingException {
        final Platform platform = (Platform) samlObject;
        ElementSupport.appendTextContent(domElement, platform.getValue().toString());
    }
}
