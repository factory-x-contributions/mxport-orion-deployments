package org.eclipse.edc.industrial.wss.server;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.jetty.websocket.api.Session;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages active WebSocket sessions for the industrial connector.
 * Provides methods for session registration, broadcasting, and targeted messaging.
 */
public class WebSocketSessionManager implements IndustrialWebSocketService {

    private final Monitor monitor;
    private final ConcurrentMap<String, Session> activeSessions;

    public WebSocketSessionManager(Monitor monitor) {
        this.monitor = monitor;
        this.activeSessions = new ConcurrentHashMap<>();
    }

    /**
     * Register a new WebSocket session
     */
    public void registerSession(String clientId, Session session) {
        activeSessions.put(clientId, session);
        monitor.info("Registered WebSocket session for client: " + clientId +
                    " (Total active sessions: " + activeSessions.size() + ")");
    }

    /**
     * Unregister a WebSocket session
     */
    public void unregisterSession(String clientId) {
        Session removed = activeSessions.remove(clientId);
        if (removed != null) {
            monitor.info("Unregistered WebSocket session for client: " + clientId +
                        " (Remaining sessions: " + activeSessions.size() + ")");
        }
    }

    /**
     * Get a session by client ID
     */
    public Session getSession(String clientId) {
        return activeSessions.get(clientId);
    }

    /**
     * Check if a client has an active session
     */
    public boolean hasSession(String clientId) {
        Session session = activeSessions.get(clientId);
        return session != null && session.isOpen();
    }

    /**
     * Get all active sessions
     */
    public Collection<Session> getAllSessions() {
        return activeSessions.values();
    }

    /**
     * Get count of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Get all active client IDs
     */
    @Override
    public Collection<String> getActiveClientIds() {
        return activeSessions.keySet();
    }

    /**
     * Broadcast a message to all connected clients
     */
    public void broadcast(String message) {
        int successCount = 0;
        int failCount = 0;

        for (Session session : activeSessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendText(message, org.eclipse.jetty.websocket.api.Callback.NOOP);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    monitor.warning("Failed to broadcast message to session", e);
                }
            }
        }

        monitor.debug(String.format("Broadcast message sent to %d clients (%d failed)",
                     successCount, failCount));
    }

    /**
     * Send a message to a specific client
     */
    public boolean sendToClient(String clientId, String message) {
        Session session = activeSessions.get(clientId);
        if (session != null && session.isOpen()) {
            try {
                session.sendText(message, org.eclipse.jetty.websocket.api.Callback.NOOP);
                monitor.debug("Sent message to client: " + clientId);
                return true;
            } catch (Exception e) {
                monitor.warning("Failed to send message to client: " + clientId, e);
                return false;
            }
        }
        monitor.debug("Cannot send message - client not connected: " + clientId);
        return false;
    }

    /**
     * Close all active sessions
     */
    public void closeAllSessions() {
        monitor.info("Closing all WebSocket sessions (" + activeSessions.size() + " active)");

        activeSessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.close();
                } catch (Exception e) {
                    monitor.warning("Error closing WebSocket session", e);
                }
            }
        });

        activeSessions.clear();
        monitor.info("All WebSocket sessions closed");
    }

    /**
     * Close session for a specific client
     */
    public void closeSession(String clientId) {
        Session session = activeSessions.remove(clientId);
        if (session != null && session.isOpen()) {
            try {
                session.close();
                monitor.info("Closed WebSocket session for client: " + clientId);
            } catch (Exception e) {
                monitor.warning("Error closing WebSocket session for client: " + clientId, e);
            }
        }
    }
}
