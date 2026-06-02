
package org.eclipse.edc.common.spi.config;

public class IndustrialConnectorResolverConfigImpl {

    private Boolean certBasedAuthenticationEnabled;
    private String sinkServiceUrl;
    private String sinkServicePushUsername;
    private String sinkServicePushPassword;
    private String sinkServiceAdminUsername;
    private String sinkServiceAdminPassword;
    private String sinkServiceAdminCertificatePath;
    private String sinkServiceAdminCertificateKeyPath;
    private String sinkServicePushUserCertificatePath;
    private String sinkServicePushUserCertificateKeyPath;
    private String sinkServiceCaChainCertificatePath;
    private String pkiEndpointUrl;
    private String pkiEndpointKey;

    public Boolean getCertBasedAuthenticationEnabled() {
        return certBasedAuthenticationEnabled;
    }

    public void setCertBasedAuthenticationEnabled(Boolean certBasedAuthenticationEnabled) {
        this.certBasedAuthenticationEnabled = certBasedAuthenticationEnabled;
    }

    public String getSinkServiceUrl() {
        return sinkServiceUrl;
    }

    public void setSinkServiceUrl(String sinkServiceUrl) {
        this.sinkServiceUrl = sinkServiceUrl;
    }

    public String getSinkServicePushUsername() {
        return sinkServicePushUsername;
    }

    public void setSinkServicePushUsername(String sinkServicePushUsername) {
        this.sinkServicePushUsername = sinkServicePushUsername;
    }

    public String getSinkServicePushPassword() {
        return sinkServicePushPassword;
    }

    public void setSinkServicePushPassword(String sinkServicePushPassword) {
        this.sinkServicePushPassword = sinkServicePushPassword;
    }

    public String getSinkServiceAdminUsername() {
        return sinkServiceAdminUsername;
    }

    public void setSinkServiceAdminUsername(String sinkServiceAdminUsername) {
        this.sinkServiceAdminUsername = sinkServiceAdminUsername;
    }

    public String getSinkServiceAdminPassword() {
        return sinkServiceAdminPassword;
    }

    public void setSinkServiceAdminPassword(String sinkServiceAdminPassword) {
        this.sinkServiceAdminPassword = sinkServiceAdminPassword;
    }

    public String getSinkServiceAdminCertificatePath() {
        return sinkServiceAdminCertificatePath;
    }

    public void setSinkServiceAdminCertificatePath(String sinkServiceAdminCertificatePath) {
        this.sinkServiceAdminCertificatePath = sinkServiceAdminCertificatePath;
    }

    public String getSinkServiceAdminCertificateKeyPath() {
        return sinkServiceAdminCertificateKeyPath;
    }

    public void setSinkServiceAdminCertificateKeyPath(String sinkServiceAdminCertificateKeyPath) {
        this.sinkServiceAdminCertificateKeyPath = sinkServiceAdminCertificateKeyPath;
    }

    public String getSinkServicePushUserCertificatePath() {
        return sinkServicePushUserCertificatePath;
    }

    public void setSinkServicePushUserCertificatePath(String sinkServicePushUserCertificatePath) {
        this.sinkServicePushUserCertificatePath = sinkServicePushUserCertificatePath;
    }

    public String getSinkServicePushUserCertificateKeyPath() {
        return sinkServicePushUserCertificateKeyPath;
    }

    public void setSinkServicePushUserCertificateKeyPath(String sinkServicePushUserCertificateKeyPath) {
        this.sinkServicePushUserCertificateKeyPath = sinkServicePushUserCertificateKeyPath;
    }

    public String getSinkServiceCaChainCertificatePath() {
        return sinkServiceCaChainCertificatePath;
    }

    public void setSinkServiceCaChainCertificatePath(String sinkServiceCaChainCertificatePath) {
        this.sinkServiceCaChainCertificatePath = sinkServiceCaChainCertificatePath;
    }

    public String getPkiEndpointUrl() {
        return pkiEndpointUrl;
    }

    public void setPkiEndpointUrl(String pkiEndpointUrl) {
        this.pkiEndpointUrl = pkiEndpointUrl;
    }

    public String getPkiEndpointKey() {
        return pkiEndpointKey;
    }

    public void setPkiEndpointKey(String pkiEndpointKey) {
        this.pkiEndpointKey = pkiEndpointKey;
    }
}