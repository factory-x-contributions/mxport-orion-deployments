package org.eclipse.edc.common.spi.security;

public interface SecurityServiceProvisionerService<T extends Credentials, R extends SecurityRequest> {
    SecurityService<T, R> getSecurityService();
}
