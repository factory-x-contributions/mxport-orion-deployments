package org.eclipse.edc.industrial.local.dataflow.mqttpush;

import org.eclipse.edc.spi.types.domain.DataAddress;

public interface IndustrialConnectorLocalPushService {

    /**
     * Starts pushing OPC UA values to MQTT for the given asset.
     * Only one push task per assetId (topic) will be active at a time.
     * If a push task already exists for this assetId, it returns success without duplicating.
     *
     * @param transferId the transfer identifier
     * @param assetId the asset identifier (used as MQTT topic)
     * @param opcUaSource the OPC UA data source address
     */
    void startPushing(String transferId, String assetId, DataAddress opcUaSource);

    /**
     * Stops pushing OPC UA values for the given transfer.
     * The push service may continue if other transfers are using the same asset topic.
     *
     * @param transferId the transfer identifier
     */
    void stopPushing(String transferId);

    /**
     * Checks if a push task is active for the given transfer.
     *
     * @param transferId the transfer identifier
     * @return true if active, false otherwise
     */
    boolean isActive(String transferId);

    /**
     * Checks if a push task is active for the given asset/topic.
     *
     * @param assetId the asset identifier
     * @return true if active, false otherwise
     */
    boolean isActiveForAsset(String assetId);
}
