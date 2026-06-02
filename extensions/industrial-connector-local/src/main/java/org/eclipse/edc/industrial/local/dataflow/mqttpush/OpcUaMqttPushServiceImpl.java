package org.eclipse.edc.industrial.local.dataflow.mqttpush;

import org.eclipse.edc.common.spi.mqttclient.MqttClient;
import org.eclipse.edc.industrial.local.dataflow.opcua.OpcUaClientService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.eclipse.edc.common.spi.helpers.Helpers.firstNonBlank;

/**
 * Pushes values read from an OPC UA server to a provider-managed MQTT broker.
 *
 * Key characteristics:
 * - One push service per assetId (topic) - prevents duplicates
 * - Multiple transfers can share the same asset/topic
 * - MQTT broker is provider-managed, accessed via environment variables
 * - Consumers receive MQTT broker credentials and topic permissions during transfer
 * - Supports single or multiple OPC UA nodes with periodic push intervals
 */
public class OpcUaMqttPushServiceImpl implements IndustrialConnectorLocalPushService {

    private static final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/";

    // Maps transferId -> assetId
    private final ConcurrentHashMap<String, String> transferToAssetMapping = new ConcurrentHashMap<>();

    // Maps assetId -> AssetPushTask (singleton per asset)
    private final ConcurrentHashMap<String, AssetPushTask> assetPushTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private final OpcUaClientService opcUaClient;
    private final MqttClient mqttClient;
    private final MqttBrokerConfig brokerConfig;
    private final Monitor monitor;

    public OpcUaMqttPushServiceImpl(OpcUaClientService opcUaClient, MqttClient mqttClient,
                                    MqttBrokerConfig brokerConfig, Monitor monitor) {
        this.opcUaClient = opcUaClient;
        this.mqttClient = mqttClient;
        this.brokerConfig = brokerConfig;
        this.monitor = monitor;
    }

    @Override
    public void startPushing(String transferId, String assetId, DataAddress opcUaSource) {
        // Ensure assetId is provided
        if (assetId == null || assetId.trim().isEmpty()) {
            monitor.severe("Asset ID must not be null/blank for transfer " + transferId);
            return;
        }

        // Check if broker is configured
        monitor.debug("Checking broker configuration for transfer " + transferId + ": " + brokerConfig);
        if (!brokerConfig.isConfigured()) {
            monitor.severe("MQTT broker not configured for transfer " + transferId + ". brokerUrl: " +
                    (brokerConfig.getBrokerUrl() != null ? brokerConfig.getBrokerUrl() : "null"));
            return;
        }

        monitor.debug("Broker is configured. Starting push for transfer " + transferId + " to asset " + assetId);

        // Track transfer -> asset mapping
        transferToAssetMapping.put(transferId, assetId);

        // Check if push task already exists for this asset
        if (assetPushTasks.containsKey(assetId)) {
            var existingTask = assetPushTasks.get(assetId);
            existingTask.addTransferId(transferId);
            monitor.info("Transfer " + transferId + " added to existing MQTT push task for asset: " + assetId +
                    " (total transfers: " + existingTask.getTransferCount() + ")");
            return;
        }

        // Extract OPC UA configuration
        String serverUrl = firstNonBlank(
                opcUaSource.getStringProperty("serverUrl"),
                opcUaSource.getStringProperty(EDC_NAMESPACE + "serverUrl")
        );

        String nodeIdSpec = firstNonBlank(
                opcUaSource.getStringProperty("nodeId"),
                opcUaSource.getStringProperty(EDC_NAMESPACE + "nodeId"),
                opcUaSource.getStringProperty("nodeIds"),
                opcUaSource.getStringProperty(EDC_NAMESPACE + "nodeIds")
        );

        // Extract push interval from asset metadata
        long intervalMs;
        try {
            String pushIntervalStr = opcUaSource.getStringProperty("pushInterval");
            intervalMs = (pushIntervalStr != null) ? Long.parseLong(pushIntervalStr) : 5000L;
        } catch (NumberFormatException e) {
            monitor.warning("Invalid pushInterval value for asset " + assetId + ", using default 5000ms", e);
            intervalMs = 5000L;
        }

        // Parse node IDs
        List<String> nodeIds = parseNodeIds(nodeIdSpec);
        if (nodeIds.isEmpty()) {
            monitor.severe("No node IDs specified for asset " + assetId);
            return;
        }

        monitor.info("Creating new OPC UA -> MQTT push task for asset: " + assetId +
                " (server: " + serverUrl + ", nodes: " + nodeIds +
                ", interval: " + intervalMs + "ms, broker: " + brokerConfig.getBrokerUrl() + ")");

        // Create the scheduled push task
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                String jsonPayload = readAndFormatOpcUaValues(serverUrl, nodeIds, assetId);
                mqttClient.publish(assetId,
                        jsonPayload.getBytes(StandardCharsets.UTF_8),
                        brokerConfig.getUsername(), brokerConfig.getPassword());

                monitor.debug("Published OPC UA data to MQTT topic '" + assetId + "' for " +
                        assetPushTasks.get(assetId).getTransferCount() + " active transfer(s)");
            } catch (Exception e) {
                monitor.severe("Failed to publish OPC UA data to MQTT for asset " + assetId, e);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        // Store the push task with this transfer
        AssetPushTask pushTask = new AssetPushTask(assetId, opcUaSource, task, intervalMs);
        pushTask.addTransferId(transferId);
        assetPushTasks.put(assetId, pushTask);

        monitor.info("Started OPC UA -> MQTT push task for asset: " + assetId);
    }

    /**
     * Reads values from OPC UA nodes and formats them as JSON.
     */
    private String readAndFormatOpcUaValues(String serverUrl, List<String> nodeIds, String assetId) throws Exception {
        Instant timestamp = Instant.now();

        if (nodeIds.size() == 1) {
            // Single node - return simple format
            Object value = opcUaClient.readValue(serverUrl, nodeIds.get(0));
            return formatOpcUaValue(nodeIds.get(0), value, timestamp, assetId);
        } else {
            // Multiple nodes - return array format
            StringBuilder sb = new StringBuilder("{\"timestamp\":\"");
            sb.append(timestamp).append("\",\"assetId\":\"").append(assetId).append("\",\"values\":[");

            for (int i = 0; i < nodeIds.size(); i++) {
                if (i > 0) sb.append(",");
                try {
                    Object value = opcUaClient.readValue(serverUrl, nodeIds.get(i));
                    sb.append("{\"nodeId\":\"").append(escapeJson(nodeIds.get(i))).append("\",\"value\":").append(formatJsonValue(value)).append("}");
                } catch (Exception e) {
                    monitor.warning("Failed to read node " + nodeIds.get(i) + " for asset " + assetId, e);
                    sb.append("{\"nodeId\":\"").append(escapeJson(nodeIds.get(i))).append("\",\"error\":\"").append(escapeJson(e.getMessage())).append("\"}");
                }
            }

            sb.append("]}");
            return sb.toString();
        }
    }

    /**
     * Formats a single OPC UA value as JSON.
     */
    private String formatOpcUaValue(String nodeId, Object value, Instant timestamp, String assetId) {
        return "{" +
                "\"nodeId\":\"" + escapeJson(nodeId) + "\"," +
                "\"value\":" + formatJsonValue(value) + "," +
                "\"timestamp\":\"" + timestamp + "\"," +
                "\"assetId\":\"" + escapeJson(assetId) + "\"" +
                "}";
    }

    /**
     * Formats a value for JSON (handles numbers vs strings properly).
     */
    private String formatJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        // Try to parse as number
        if (value instanceof Number) {
            return value.toString();
        }
        String strValue = value.toString();
        try {
            Double.parseDouble(strValue);
            return strValue;
        } catch (NumberFormatException e) {
            return "\"" + escapeJson(strValue) + "\"";
        }
    }

    /**
     * Parses comma-separated node IDs.
     */
    private List<String> parseNodeIds(String nodeIdSpec) {
        if (nodeIdSpec == null || nodeIdSpec.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(nodeIdSpec.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Escapes special JSON characters in strings.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void stopPushing(String transferId) {
        String assetId = transferToAssetMapping.remove(transferId);
        if (assetId == null) {
            return;
        }

        var pushTask = assetPushTasks.get(assetId);
        if (pushTask == null) {
            return;
        }

        pushTask.removeTransferId(transferId);
        monitor.info("Transfer " + transferId + " removed from MQTT push task for asset: " + assetId +
                " (remaining transfers: " + pushTask.getTransferCount() + ")");

        // Only stop the scheduled task if no transfers are using this asset
        if (!pushTask.hasTransfers()) {
            pushTask.getScheduledTask().cancel(true);
            assetPushTasks.remove(assetId);
            monitor.info("Stopped OPC UA -> MQTT push task for asset: " + assetId);
        }
    }

    @Override
    public boolean isActive(String transferId) {
        return transferToAssetMapping.containsKey(transferId);
    }

    @Override
    public boolean isActiveForAsset(String assetId) {
        var pushTask = assetPushTasks.get(assetId);
        return pushTask != null && pushTask.isRunning();
    }
}
