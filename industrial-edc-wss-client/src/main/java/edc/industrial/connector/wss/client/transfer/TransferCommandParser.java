package edc.industrial.connector.wss.client.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw JSON WebSocket messages into {@link TransferCommand} objects.
 *
 * Compatible with the command format emitted by {@code IndustrialConnectorWssTransferFlowImpl}.
 */
public class TransferCommandParser {

    private static final Logger log = LoggerFactory.getLogger(TransferCommandParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final java.util.Set<String> IGNORED_TYPES = java.util.Set.of(
            "pong", "welcome", "error", "ack", "subscribed", "unsubscribed",
            "response", "transfer_ack"
    );

    /**
     * Parses a JSON string into a {@link TransferCommand}.
     * Returns a command of type {@code UNKNOWN} if the message cannot be parsed or the type is unrecognised.
     */
    public TransferCommand parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = root.path("type").asText("unknown");

            return switch (type.toLowerCase()) {
                case "opcua_read_request" -> parseOpcUaReadRequest(root);
                case "suspend_transfer"   -> parseSimpleCommand(root, TransferCommand.Type.SUSPEND_TRANSFER);
                case "terminate_transfer" -> parseSimpleCommand(root, TransferCommand.Type.TERMINATE_TRANSFER);
                default -> {
                    if (!IGNORED_TYPES.contains(type.toLowerCase())) {
                        log.debug("Unrecognised message type: {}", type);
                    }
                    yield TransferCommand.builder(TransferCommand.Type.UNKNOWN)
                            .transferId(root.path("transferId").asText(null))
                            .build();
                }
            };
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message: {}", json, e);
            return TransferCommand.builder(TransferCommand.Type.UNKNOWN).build();
        }
    }

    // ---- private helpers -----------------------------------------------------------

    private TransferCommand parseOpcUaReadRequest(JsonNode root) {
        String transferId = root.path("transferId").asText(null);
        String opcuaServer = root.path("opcuaServer").asText(null);
        String mqttBroker = root.path("mqttBroker").asText(null);
        String mqttTopic = root.path("mqttTopic").asText(null);
        long pushInterval = root.path("pushInterval").asLong(5000);

        // nodeIds can be:
        //   (a) a proper JSON array: ["ns=14;i=2259", "ns=14;i=2258", ...]
        //   (b) a JSON array whose single element is a comma-separated list:
        //       ["ns=14;i=2259, ns=14;i=2258, ..."]  ← server-side serialisation quirk
        //   (c) a plain JSON string: "ns=14;i=2259, ns=14;i=2258, ..."
        // In all cases we normalise to a flat list of trimmed, non-blank node ID strings.
        List<String> nodeIds = new ArrayList<>();
        JsonNode nodeIdsNode = root.path("nodeIds");
        if (nodeIdsNode.isArray()) {
            for (JsonNode n : nodeIdsNode) {
                String v = n.asText(null);
                if (v != null && !v.isBlank()) {
                    // Each array element might itself be a comma-separated list (case b)
                    for (String part : v.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) nodeIds.add(trimmed);
                    }
                }
            }
        } else if (!nodeIdsNode.isMissingNode()) {
            String v = nodeIdsNode.asText(null);
            if (v != null && !v.isBlank()) {
                // comma-separated plain string (case c)
                for (String part : v.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) nodeIds.add(trimmed);
                }
            }
        }
        if (!nodeIds.isEmpty()) {
            log.debug("Parsed {} nodeId(s): {}", nodeIds.size(), nodeIds);
        }

        // Auth details
        String authType = root.path("authType").asText(null);
        String username = root.path("username").asText(null);
        String password = root.path("password").asText(null);
        String certificate = root.path("certificate").asText(null);
        String caChain = root.path("caChain").asText(null);

        return TransferCommand.builder(TransferCommand.Type.OPCUA_READ_REQUEST)
                .transferId(transferId)
                .opcuaServer(opcuaServer)
                .nodeIds(nodeIds)
                .mqttBroker(mqttBroker)
                .mqttTopic(mqttTopic)
                .pushIntervalMs(pushInterval)
                .authType(authType)
                .username(username)
                .password(password)
                .certificate(certificate)
                .caChain(caChain)
                .build();
    }

    private TransferCommand parseSimpleCommand(JsonNode root, TransferCommand.Type type) {
        String transferId = root.path("transferId").asText(null);
        return TransferCommand.builder(type).transferId(transferId).build();
    }
}

