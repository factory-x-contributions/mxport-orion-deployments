package org.eclipse.edc.industrial.wss.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.time.Instant;

/**
 * WebSocket endpoint handler for industrial communication.
 * Handles connection lifecycle and message processing.
 */
@WebSocket
public class IndustrialWebSocketEndpoint {

    private final String clientId;
    private final WebSocketSessionManager sessionManager;
    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    private Session session;

    public IndustrialWebSocketEndpoint(String clientId, WebSocketSessionManager sessionManager, Monitor monitor) {
        this.clientId = clientId;
        this.sessionManager = sessionManager;
        this.monitor = monitor;
        this.objectMapper = new ObjectMapper();
    }

    @OnWebSocketOpen
    public void onOpen(Session session) {
        this.session = session;
        sessionManager.registerSession(clientId, session);
        monitor.info("WebSocket connected for client: " + clientId);

        // Send welcome message
        sendWelcomeMessage();
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        monitor.debug("Received WebSocket message from " + clientId + ": " + message);

        try {
            // Parse JSON message
            JsonNode jsonNode = objectMapper.readTree(message);
            String messageType = jsonNode.has("type") ? jsonNode.get("type").asText() : "unknown";

            // Handle different message types
            switch (messageType) {
                case "ping":
                    handlePing(jsonNode);
                    break;
                case "subscribe":
                    handleSubscribe(jsonNode);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(jsonNode);
                    break;
                case "request":
                    handleRequest(jsonNode);
                    break;
                case "data":
                    handleData(jsonNode);
                    break;
                default:
                    handleUnknownMessage(message);
                    break;
            }
        } catch (Exception e) {
            monitor.warning("Error processing WebSocket message from " + clientId, e);
            sendErrorResponse("Error processing message: " + e.getMessage());
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        sessionManager.unregisterSession(clientId);
        monitor.info(String.format("WebSocket closed for client: %s (code: %d, reason: %s)", 
                    clientId, statusCode, reason));
    }

    @OnWebSocketError
    public void onError(Throwable error) {
        monitor.warning("WebSocket error for client: " + clientId, error);
        sessionManager.unregisterSession(clientId);
    }

    private void sendWelcomeMessage() {
        String welcomeMessage = String.format(
                "{\"type\":\"welcome\",\"clientId\":\"%s\",\"timestamp\":\"%s\",\"message\":\"Connected to Industrial WebSocket Connector\"}",
                clientId, Instant.now().toString()
        );
        sendMessage(welcomeMessage);
    }

    private void handlePing(JsonNode message) {
        String pongMessage = String.format(
                "{\"type\":\"pong\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                clientId, Instant.now().toString()
        );
        sendMessage(pongMessage);
    }

    private void handleSubscribe(JsonNode message) {
        String topic = message.has("topic") ? message.get("topic").asText() : "unknown";
        monitor.info("Client " + clientId + " subscribed to topic: " + topic);

        String response = String.format(
                "{\"type\":\"subscribed\",\"topic\":\"%s\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                topic, clientId, Instant.now().toString()
        );
        sendMessage(response);
    }

    private void handleUnsubscribe(JsonNode message) {
        String topic = message.has("topic") ? message.get("topic").asText() : "unknown";
        monitor.info("Client " + clientId + " unsubscribed from topic: " + topic);

        String response = String.format(
                "{\"type\":\"unsubscribed\",\"topic\":\"%s\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                topic, clientId, Instant.now().toString()
        );
        sendMessage(response);
    }

    private void handleRequest(JsonNode message) {
        String requestId = message.has("requestId") ? message.get("requestId").asText() : "unknown";
        monitor.debug("Client " + clientId + " sent request: " + requestId);

        // TODO: Process the request (e.g., read OPC UA values, query data, etc.)

        String response = String.format(
                "{\"type\":\"response\",\"requestId\":\"%s\",\"clientId\":\"%s\",\"status\":\"success\",\"timestamp\":\"%s\"}",
                requestId, clientId, Instant.now().toString()
        );
        sendMessage(response);
    }

    private void handleData(JsonNode message) {
        monitor.debug("Client " + clientId + " sent data message");
        // TODO: Process data message (e.g., store, forward, process)

        String ackMessage = String.format(
                "{\"type\":\"ack\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                clientId, Instant.now().toString()
        );
        sendMessage(ackMessage);
    }

    private void handleUnknownMessage(String message) {
        monitor.warning("Unknown message type from client " + clientId + ": " + message);
        sendErrorResponse("Unknown message type");
    }

    private void sendErrorResponse(String error) {
        String errorMessage = String.format(
                "{\"type\":\"error\",\"clientId\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                clientId, error, Instant.now().toString()
        );
        sendMessage(errorMessage);
    }

    private void sendMessage(String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendText(message, Callback.NOOP);
            } catch (Exception e) {
                monitor.warning("Failed to send message to client: " + clientId, e);
            }
        }
    }

    public String getClientId() {
        return clientId;
    }
}
