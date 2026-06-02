package org.eclipse.edc.industrial.local.dataflow.mqttpush;

import org.eclipse.edc.spi.types.domain.DataAddress;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * Represents a single push task that publishes OPC UA data for a specific asset to an MQTT topic.
 * Multiple transfers can reference the same asset, but only one push task runs per asset/topic.
 */
public class AssetPushTask {

    private final String assetId;
    private final DataAddress opcUaSource;
    private final ScheduledFuture<?> scheduledTask;
    private final Set<String> transferIds;
    private final long pushIntervalMs;

    public AssetPushTask(String assetId, DataAddress opcUaSource, ScheduledFuture<?> scheduledTask, long pushIntervalMs) {
        this.assetId = assetId;
        this.opcUaSource = opcUaSource;
        this.scheduledTask = scheduledTask;
        this.pushIntervalMs = pushIntervalMs;
        this.transferIds = new HashSet<>();
    }

    public String getAssetId() {
        return assetId;
    }

    public DataAddress getOpcUaSource() {
        return opcUaSource;
    }

    public ScheduledFuture<?> getScheduledTask() {
        return scheduledTask;
    }

    public long getPushIntervalMs() {
        return pushIntervalMs;
    }

    public Set<String> getTransferIds() {
        return new HashSet<>(transferIds);
    }

    public void addTransferId(String transferId) {
        transferIds.add(transferId);
    }

    public boolean removeTransferId(String transferId) {
        return transferIds.remove(transferId);
    }

    public boolean hasTransfers() {
        return !transferIds.isEmpty();
    }

    public int getTransferCount() {
        return transferIds.size();
    }

    public boolean isRunning() {
        return !scheduledTask.isCancelled() && !scheduledTask.isDone();
    }

    @Override
    public String toString() {
        return "AssetPushTask{" +
                "assetId='" + assetId + '\'' +
                ", transferCount=" + transferIds.size() +
                ", running=" + isRunning() +
                ", intervalMs=" + pushIntervalMs +
                '}';
    }
}

