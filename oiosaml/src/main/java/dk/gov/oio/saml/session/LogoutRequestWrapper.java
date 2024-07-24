package dk.gov.oio.saml.session;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.opensaml.saml.saml2.core.BaseID;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.SessionIndex;

import dk.gov.oio.saml.util.InternalException;
import dk.gov.oio.saml.util.StringUtil;

public class LogoutRequestWrapper {

    private LogoutRequest delegator;

    public LogoutRequestWrapper(LogoutRequest delegator) {
        this.delegator = delegator;
    }

    public LogoutRequest getLogoutRequest() {
        return delegator;
    }

    public String getLogoutRequestAsBase64() throws InternalException {
        return StringUtil.xmlObjectToBase64(delegator);
    }

    public String getIssuerAsString() {
        return delegator.getIssuer() != null ?
                delegator.getIssuer().getValue() : "";
    }

    public String getIssueInstantAsString() {
        return delegator.getIssueInstant() != null ?
                delegator.getIssueInstant().toString() : "";
    }

    public String getSessionIndexesAsString() {
        return delegator.getSessionIndexes()
                .stream()
                .map(sessionIndex -> sessionIndex.getValue())
                .collect(Collectors
                        .joining(", ", "[", "]"));
    }

    public String getSignatureReferenceID() {
        return delegator.getSignatureReferenceID();
    }

    public String getReason() {
        return delegator.getReason();
    }

    public Instant getNotOnOrAfter() {
        return delegator.getNotOnOrAfter();
    }

    public BaseID getBaseID() {
        return delegator.getBaseID();
    }

    public NameID getNameID() {
        return delegator.getNameID();
    }

    public List<SessionIndex> getSessionIndexes() {
        return delegator.getSessionIndexes();
    }

    public String getID() {
        return delegator.getID();
    }

    public Instant getIssueInstant() {
        return delegator.getIssueInstant();
    }

    public String getDestination() {
        return delegator.getDestination();
    }

    public Issuer getIssuer() {
        return delegator.getIssuer();
    }
}
