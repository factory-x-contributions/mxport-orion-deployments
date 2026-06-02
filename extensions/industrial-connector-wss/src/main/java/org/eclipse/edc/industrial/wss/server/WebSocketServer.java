package org.eclipse.edc.industrial.wss.server;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

/**
 * Standalone WebSocket server using Jetty 12.
 */
public class WebSocketServer {

    private final int port;
    private final String path;
    private final WebSocketSessionManager sessionManager;
    private final Monitor monitor;
    private Server server;

    public WebSocketServer(int port, String path, WebSocketSessionManager sessionManager, Monitor monitor) {
        this.port = port;
        this.path = path;
        this.sessionManager = sessionManager;
        this.monitor = monitor;
    }

    public void start() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        // Create WebSocket upgrade handler
        WebSocketUpgradeHandler wsHandler = WebSocketUpgradeHandler.from(server, container -> {
            container.addMapping(path, (upgradeRequest, upgradeResponse, callback) -> {
                // Extract clientId from query parameters (e.g., /ws?clientId=myClient)
                String clientId = null;
                String queryString = upgradeRequest.getHttpURI().getQuery();
                if (queryString != null) {
                    String[] params = queryString.split("&");
                    for (String param : params) {
                        String[] keyValue = param.split("=", 2);
                        if (keyValue.length == 2 && "clientId".equals(keyValue[0])) {
                            clientId = keyValue[1];
                            break;
                        }
                    }
                }
                if (clientId == null || clientId.trim().isEmpty()) {
                    clientId = "client-" + System.currentTimeMillis();
                }
                monitor.info("Creating WebSocket for client: " + clientId);
                return new IndustrialWebSocketEndpoint(clientId, sessionManager, monitor);
            });

            container.setIdleTimeout(java.time.Duration.ofSeconds(300));
            container.setMaxTextMessageSize(65536);
        });

        server.setHandler(wsHandler);
        server.start();

        monitor.info("WebSocket server started on port " + port + " at path " + path);
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}
