package org.eclipse.edc.common.spi.dataflow;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Generic service for handling data transfer flow operations.
 * Implementations can handle different transfer types and protocols.
 */
public interface TransferFlowService {

    /**
     * Determines if this service can handle the given transfer process.
     *
     * @param transferProcess the transfer process to check
     * @return true if this service can handle the transfer, false otherwise
     */
    boolean canHandle(@NotNull TransferProcess transferProcess);

    /**
     * Starts a transfer process.
     *
     * @param transferProcess the transfer process to start
     * @return a status result containing the data flow response
     */
    @NotNull
    StatusResult<DataFlowResponse> startTransfer(@NotNull TransferProcess transferProcess);

    /**
     * Suspends an active transfer process.
     *
     * @param transferProcess the transfer process to suspend
     * @return a status result indicating success or failure
     */
    @NotNull
    StatusResult<Void> suspendTransfer(@NotNull TransferProcess transferProcess);

    /**
     * Terminates a transfer process and cleans up associated resources.
     *
     * @param transferProcess the transfer process to terminate
     * @return a status result indicating success or failure
     */
    @NotNull
    StatusResult<Void> terminateTransfer(@NotNull TransferProcess transferProcess);

    /**
     * Returns the supported transfer types for the given asset.
     *
     * @param asset the asset to get transfer types for
     * @return a set of supported transfer type identifiers
     */
    @NotNull
    Set<String> getSupportedTransferTypes(@NotNull Asset asset);

    /**
     * Builds a protocol-specific read command for client execution.
     *
     * @param transferId the transfer identifier
     * @param contentDataAddress the data address containing protocol configuration
     * @param assetId thed asset identifier
     * @param brokerUrl the broker/endpoint URL
     * @return a protocol-specific command string
     */
    @NotNull
    String buildReadCommand(@NotNull String transferId,
                            @NotNull DataAddress contentDataAddress,
                            @NotNull String assetId,
                            @NotNull String brokerUrl);

    /**
     * Retrieves a property from a DataAddress with fallback to default value.
     *
     * @param dataAddress the data address to retrieve from
     * @param key the property key
     * @param defaultValue the default value if key is not found
     * @return the property value or default value
     */
    @NotNull
    String getProperty(@NotNull DataAddress dataAddress,
                       @NotNull String key,
                       @NotNull String defaultValue);
}
