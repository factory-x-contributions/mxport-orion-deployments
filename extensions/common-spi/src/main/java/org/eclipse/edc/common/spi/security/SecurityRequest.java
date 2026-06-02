package org.eclipse.edc.common.spi.security;

/**
 * Generic request describing a security provisioning operation.
 */
public class SecurityRequest {

    private String transferId;

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }
}