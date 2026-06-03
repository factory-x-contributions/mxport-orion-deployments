# Industrial Connector WSS Client

Standalone Java application that acts as the **field-side agent** for the
Industrial Eclipse Dataspace Connector and its industrial
WebSocket extension (`IndustrialWebSocketExtension`).

## What it does

```
EDC Connector (server)
  └─ IndustrialConnectorWssTransferFlowImpl
       │  (sends JSON commands over WSS)
       ▼
Industrial Connector WSS Client (this app)   ◄──── wss://host:port/industrial-ws?apiKey=...
  ├─ Receives  opcua_read_request  → starts periodic OPC-UA → MQTT push
  ├─ Receives  suspend_transfer    → pauses the push task
  └─ Receives  terminate_transfer  → stops and cleans up the push task
```

The application connects to the EDC WSS server using an **ENDPOINT + API_KEY**
authentication scheme (URL query param `apiKey` + HTTP header `X-Api-Key`).

When the server sends an `opcua_read_request` command, the client:

1. Connects to the specified OPC-UA server
2. Reads the configured node IDs at the specified interval
3. Formats values as JSON
4. Publishes them to the specified MQTT topic/broker

## Prerequisites

- Java 17+
- [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) included (`./gradlew`) — no local Gradle install needed
- Docker Desktop (for container builds)

## Building

### Build the fat JAR (required before Docker)

```bash
./gradlew shadowJar
```

Output: `build/libs/industrial-connector-wss-client-1.0.0-SNAPSHOT-standalone.jar`

### Run tests + full build

```bash
./gradlew build
```

## Running locally

### Option 1 – Gradle task (recommended for development)

Edit `client.local.env` with your local settings, then:

```bash
./gradlew runLocal
```

This builds the fat JAR and runs it with `WSS_CLIENT_ENV_FILE=client.local.env`.

### Option 2 – Fast dev mode (no fat JAR rebuild)

```bash
./gradlew runDev
```

Runs directly from the compiled classpath — faster iteration, uses `client.local.env`.

### Option 3 – Run the JAR manually

```bash
./gradlew shadowJar

WSS_ENDPOINT="ws://my-connector:8181/industrial-ws" \
WSS_API_KEY="secret" \
java -jar build/libs/industrial-connector-wss-client-1.0.0-SNAPSHOT-standalone.jar
```

Or point at an env file:

```bash
WSS_CLIENT_ENV_FILE=client.local.env \
java -jar build/libs/industrial-connector-wss-client-1.0.0-SNAPSHOT-standalone.jar
```

## Running with Docker

> **Prerequisites:** The [Minimum Viable Dataspace (MVD)](https://github.com/eclipse-edc/MinimumViableDataspace)
> stack must be running in Docker before starting this client. It provides the EDC connector
> (`provider-connector-qna`), MQTT broker (`mosquitto`), and OPC-UA server (`opcua-server`)
> that this client connects to.
>
> Start the MVD stack first:
> ```bash
> # inside the MVD / deployment project
> docker compose up -d
> ```
> Then confirm its network name (you'll need it in Step 4):
> ```bash
> docker network ls | grep default
> # e.g. deployment_default
> ```

> The OPC-UA server, MQTT broker, and EDC connector are expected to run **outside** this project.
> Configure their addresses in `client.docker.env` before building the image.

### Step 1 – Build the fat JAR on the host

```bash
./gradlew shadowJar
```

### Step 2 – Build the Docker image

```bash
docker build -t edc/industrial-connector-wss-client:latest .
```

### Step 3 – Configure `client.docker.env`

Set hostnames to match your running services. If the connector runs in another
Docker Compose stack on the same machine, use the service name and join its network
(see `docker-compose.standalone.yml`). If it runs on the host, use `host.docker.internal`.

```dotenv
WSS_ENDPOINT=ws://provider-connector-qna:8181/industrial-ws
WSS_API_KEY=changeme
MQTT_BROKER_URL=tcp://mosquitto:1883
OPCUA_SERVER_URL=opc.tcp://opcua-server:4840
```

### Step 4 – Start the container

```bash
docker compose -f docker-compose.standalone.yml up -d
```

Follow logs:

```bash
docker compose -f docker-compose.standalone.yml logs -f industrial-connector-wss-client
```

Stop:

```bash
docker compose -f docker-compose.standalone.yml down
```

### Connecting to another Docker Compose stack

If the EDC connector runs in a separate Compose project, join its network.
Find the network name:

```bash
docker network ls | grep default
```

Then set it in `docker-compose.standalone.yml`:

```yaml
networks:
  dataspace:
    external: true
    name: <other-project>_default   # e.g. deployment_default
```

And restart:

```bash
docker compose -f docker-compose.standalone.yml down
docker compose -f docker-compose.standalone.yml up -d
```

## Configuration

All configuration is supplied through **environment variables** (or system
properties using the lowercase dotted form, e.g. `wss.endpoint`).

| Variable | Default | Description |
|----------|---------|-------------|
| `WSS_ENDPOINT` | `ws://localhost:8181/industrial-ws` | WebSocket server URL |
| `WSS_API_KEY` | *(none)* | API key sent as query param and `X-Api-Key` header |
| `WSS_CLIENT_ID` | auto-generated | Identifier sent to the server |
| `WSS_RECONNECT_INTERVAL_MS` | `5000` | Delay between reconnect attempts |
| `MQTT_BROKER_URL` | `tcp://localhost:1883` | Fallback MQTT broker URL |
| `MQTT_USERNAME` | *(none)* | MQTT username (optional) |
| `MQTT_PASSWORD` | *(none)* | MQTT password (optional) |
| `MQTT_CA_CERT_PATH` | *(none)* | Path to CA cert for TLS MQTT |
| `MQTT_CLIENT_CERT_PATH` | *(none)* | Path to client cert for TLS MQTT |
| `MQTT_CLIENT_KEY_PATH` | *(none)* | Path to client key for TLS MQTT |
| `OPCUA_USERNAME` | *(none)* | OPC-UA username (anonymous if blank) |
| `OPCUA_PASSWORD` | *(none)* | OPC-UA password |
| `OPCUA_CLIENT_CERT_PATH` | *(none)* | Client certificate for OPC-UA TLS |
| `OPCUA_CLIENT_KEY_PATH` | *(none)* | Client private key for OPC-UA TLS |

## Supported WSS Commands

### `opcua_read_request`

Sent by the EDC connector to start a periodic push:

```json
{
  "type": "opcua_read_request",
  "transferId": "transfer-123",
  "opcuaServer": "opc.tcp://plc-host:4840",
  "nodeIds": ["ns=2;i=1001", "ns=2;i=1002"],
  "mqttBroker": "tcp://mqtt-broker:1883",
  "mqttTopic": "factory/line1/temperature",
  "pushInterval": 5000,
  "authType": "password",
  "username": "mqtt-user",
  "password": "mqtt-pass"
}
```

### `suspend_transfer`

Pauses an active push task (calls `stopPushing` internally):

```json
{ "type": "suspend_transfer", "transferId": "transfer-123" }
```

### `terminate_transfer`

Stops and removes an active push task:

```json
{ "type": "terminate_transfer", "transferId": "transfer-123" }
```

## Acknowledgement messages

The client sends back a `transfer_ack` message after each command:

```json
{
  "type": "transfer_ack",
  "transferId": "transfer-123",
  "status": "started",
  "clientId": "my-opcua-client-01",
  "timestamp": "2026-04-27T12:00:00Z"
}
```

## Architecture overview

```
WssOpcUaClientApp          – entry point; wires dependencies
  ClientConfig             – reads env vars / system props
  WssClientConnection      – manages WSS connection + reconnect loop
    TransferCommandParser  – JSON → TransferCommand
    ActiveTransferManager  – manages scheduled push tasks
      OpcUaClientService   – reads values via Eclipse Milo SDK
      MqttPublisherService – publishes via Eclipse Paho v3
```

