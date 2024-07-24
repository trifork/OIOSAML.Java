package dk.gov.oio.saml.extensions.appswitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.opensaml.core.xml.AbstractXMLObject;
import org.opensaml.core.xml.XMLObject;

public class AppSwitchImpl extends AbstractXMLObject implements AppSwitch {
    private Platform platform;
    private ReturnURL returnURL;

    protected AppSwitchImpl(@Nullable String namespaceURI, @Nonnull String elementLocalName, @Nullable String namespacePrefix) {
        super(namespaceURI, elementLocalName, namespacePrefix);
    }

    @Override
    public Platform getPlatform() {
        return platform;
    }

    @Override
    public ReturnURL getReturnURL() {
        return returnURL;
    }

    @Override
    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void setReturnURL(ReturnURL returnURL) {
        this.returnURL = returnURL;
    }

    @Nullable
    @Override
    public List<XMLObject> getOrderedChildren() {
        ArrayList<XMLObject> children = new ArrayList<>();
        children.add(this.platform);
        children.add(this.returnURL);

        return Collections.unmodifiableList(children);
    }
}

