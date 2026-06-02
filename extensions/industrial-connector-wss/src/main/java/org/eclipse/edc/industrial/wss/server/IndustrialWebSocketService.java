package org.eclipse.edc.industrial.wss.server;

import java.util.Collection;

/**
 * Service interface for WebSocket communication with industrial clients.
 * Allows other extensions to send messages to connected WebSocket clients
 * behind firewalls for distributed industrial automation and data collection.
 */
public interface IndustrialWebSocketService {

    /**
     * Broadcast a message to all connected clients
     *
     * @param message the message to broadcast
     */
    void broadcast(String message);

    /**
     * Send a message to a specific client
     *
     * @param clientId the target client identifier
     * @param message the message to send
     * @return true if message was sent successfully, false otherwise
     */
    boolean sendToClient(String clientId, String message);

    /**
     * Check if a client has an active session
     *
     * @param clientId the client identifier to check
     * @return true if client has an active session, false otherwise
     */
    boolean hasSession(String clientId);

    /**
     * Get count of active sessions
     *
     * @return number of currently active WebSocket sessions
     */
    int getActiveSessionCount();

    /**
     * Get all active client IDs
     *
     * @return collection of client identifiers with active sessions
     */
    Collection<String> getActiveClientIds();

    /**
     * Close session for a specific client
     *
     * @param clientId the client identifier whose session to close
     */
    void closeSession(String clientId);
}
