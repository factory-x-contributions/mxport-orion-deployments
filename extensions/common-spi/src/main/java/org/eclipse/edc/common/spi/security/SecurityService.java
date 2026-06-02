package org.eclipse.edc.common.spi.security;


import org.eclipse.edc.spi.result.Result;

/**
 * Generic security provisioning service.
 *
 * @param <T> credential type returned by the implementation
 */
public interface SecurityService<T extends Credentials, R extends SecurityRequest> {

    /**
     * Provisions access according to the provided security request.
     *
     * @param request security provisioning request
     * @return credentials required to access the resource
     */
    Result<T> provisionAccess(R request);

    /**
     * Revokes previously provisioned access.
     *
     * @param request revoke request
     */
    Result<Void> revokeAccess(R request);
}