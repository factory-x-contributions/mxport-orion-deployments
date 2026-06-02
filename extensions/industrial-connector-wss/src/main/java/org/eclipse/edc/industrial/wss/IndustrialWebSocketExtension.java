package org.eclipse.edc.industrial.wss;

import org.eclipse.edc.common.spi.dataflow.TransferFlowService;
import org.eclipse.edc.industrial.wss.dataflow.IndustrialConnectorWssTransferFlowImpl;
import org.eclipse.edc.industrial.wss.server.IndustrialWebSocketService;
import org.eclipse.edc.industrial.wss.server.WebSocketServer;
import org.eclipse.edc.industrial.wss.server.WebSocketSessionManager;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.eclipse.edc.industrial.wss.IndustrialWebSocketExtension.NAME;

/**
 * Industrial WebSocket Server Extension for EDC.
 * 
 * Provides a WebSocket server for real-time industrial communication.
 * Supports bidirectional messaging, session management, and secure connections.
 * 
 * Configuration properties:
 * - edc.industrial.wss.port: WebSocket server port (default: 8181)
 * - edc.industrial.wss.path: WebSocket endpoint path (default: /industrial-ws)
 * - edc.industrial.wss.idle.timeout: Connection idle timeout in seconds (default: 300)
 */
@Provides(TransferFlowService.class)
@Extension(value = NAME)
public class IndustrialWebSocketExtension implements ServiceExtension {

    public static final String NAME = "Industrial WebSocket Connector (WSS)";

    // Configuration keys
    private static final String EXTENSION_ENABLED = "edc.industrial.connector.wss.enabled";
    private static final String WS_PORT_SETTING = "edc.industrial.wss.port";
    private static final String WS_PATH_SETTING = "edc.industrial.wss.path";
    private static final String WS_IDLE_TIMEOUT_SETTING = "edc.industrial.wss.idle.timeout";

    // Default values
    private static final int DEFAULT_WS_PORT = 8181;
    private static final String DEFAULT_WS_PATH = "/industrial-ws";
    private static final int DEFAULT_IDLE_TIMEOUT = 300; // 5 minutes

    @Inject
    private Monitor monitor;

    private WebSocketSessionManager sessionManager;
    private WebSocketServer wsServer;
    private int configuredPort;
    private String configuredPath;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        // Check if extension should be enabled
        boolean extensionEnabled = context.getConfig().getBoolean(EXTENSION_ENABLED, false);
        if (!extensionEnabled) {
            monitor.info("Industrial WebSocket Extension is disabled via configuration");
            return;
        }

        configuredPort = context.getConfig().getInteger(WS_PORT_SETTING, DEFAULT_WS_PORT);
        configuredPath = context.getConfig().getString(WS_PATH_SETTING, DEFAULT_WS_PATH);
        var idleTimeout = context.getConfig().getInteger(WS_IDLE_TIMEOUT_SETTING, DEFAULT_IDLE_TIMEOUT);

        sessionManager = new WebSocketSessionManager(monitor);
        
        // Register both the interface and the concrete implementation
        context.registerService(IndustrialWebSocketService.class, sessionManager);
        context.registerService(WebSocketSessionManager.class, sessionManager);

        var transferFlowService = new IndustrialConnectorWssTransferFlowImpl(monitor, sessionManager);
        context.registerService(TransferFlowService.class, transferFlowService);

        monitor.info(String.format("Industrial WebSocket server will start on port %d, path: %s", configuredPort, configuredPath));
    }

    @Override
    public void start() {
        // Check if extension was properly initialized (not disabled via configuration)  
        if (sessionManager == null) {
            // Extension was disabled in initialize() - do nothing
            return;
        }

        try {
            wsServer = new WebSocketServer(configuredPort, configuredPath, sessionManager, monitor);
            wsServer.start();
            monitor.info("Industrial WebSocket server started successfully");
        } catch (Exception e) {
            monitor.severe("Failed to start Industrial WebSocket server", e);
        }
    }

    @Override
    public void shutdown() {
        if (wsServer != null) {
            try {
                wsServer.stop();
                monitor.info("Industrial WebSocket server stopped");
            } catch (Exception e) {
                monitor.warning("Error stopping WebSocket server", e);
            }
        }

        if (sessionManager != null) {
            sessionManager.closeAllSessions();
        }
    }
}
