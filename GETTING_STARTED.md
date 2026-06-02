# Getting Started – Zero to Running Dataspace

This guide takes you from a **fresh machine** to a **fully running Industrial Dataspace** with live OPC UA data streaming over MQTT. Follow every section in order on your first run.

---

## Table of Contents

1. [Repository Map](#1-repository-map)
2. [Prerequisites](#2-prerequisites)
   - [macOS](#macos)
   - [Linux (Ubuntu / Debian)](#linux-ubuntu--debian)
   - [Windows (WSL 2)](#windows-wsl-2)
3. [Clone the Repository](#3-clone-the-repository)
4. [Choose a Run Mode](#4-choose-a-run-mode)
5. [Path A – Docker Compose (recommended for first-timers)](#5-path-a--docker-compose-recommended-for-first-timers)
6. [Path B – Local / Host Run (recommended for development)](#6-path-b--local--host-run-recommended-for-development)
7. [Optional Companion Services](#7-optional-companion-services)
   - [WSS Client (Firewall-Traversal Mode)](#wss-client-firewall-traversal-mode)
   - [Lightweight PKI Tool (TLS MQTT Mode)](#lightweight-pki-tool-tls-mqtt-mode)
8. [Interactive Transfer Walkthrough](#8-interactive-transfer-walkthrough)
9. [Verify the Data Stream](#9-verify-the-data-stream)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Repository Map

This starter kit is the **core** of the industrial dataspace. The two optional companion services are now bundled inside this repository as subfolders, so a single clone gets you everything:

| Component | Purpose | Required for |
|---|---|---|
| Core starter kit (this repo root) — originally [industrial-minimum-viable-dataspace-starter-kit](https://github.com/Machine-Information-Interoperability/industrial-minimum-viable-dataspace-starter-kit) | Core EDC connectors, OPC UA → MQTT extension, Docker Compose stack | Always |
| [industrial-edc-wss-client/](industrial-edc-wss-client/) — originally [industrial-edc-wss-client](https://github.com/Machine-Information-Interoperability/industrial-edc-wss-client) | Edge/plant-side WebSocket client that relays OPC UA data through a firewall to the connector | Only when `edc.industrial.connector.wss.enabled=true` |
| [industrial-edc-lightweight-pki-tool/](industrial-edc-lightweight-pki-tool/) — originally [industrial-edc-lightweight-pki-tool](https://github.com/Machine-Information-Interoperability/industrial-edc-lightweight-pki-tool) | Local PKI service for issuing and signing TLS certificates used in mutual-TLS MQTT auth | Only when `edc.industrial.connector.cert.auth.enabled=true` |

---

## 2. Prerequisites

You need the following tools installed before you start.

### Minimum (Docker Compose path)

| Tool | Minimum version | Notes |
|---|---|---|
| **Git** | any | For cloning repositories |
| **Docker Desktop** (macOS/Windows) or **Docker Engine + Compose plugin** (Linux) | Docker 24+ / Compose 2.20+ | Runs all services as containers |
| **Node.js** | 18+ | Required only for `npm install -g newman` |
| **newman** | 6+ | Postman CLI used by the seed scripts |
| **curl** | any | Used by the interactive script |
| **jq** | 1.6+ | JSON processing in shell scripts |

### Additional (Local / Host path)

| Tool | Minimum version | Notes |
|---|---|---|
| **JDK 17** | 17+ | Temurin/Eclipse build of OpenJDK recommended |

---

### macOS

#### Install Homebrew (if not already installed)
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

#### Install all required tools
```bash
# Git (usually pre-installed on macOS via Xcode CLI tools)
xcode-select --install 2>/dev/null || true

# Docker Desktop – download from https://www.docker.com/products/docker-desktop/
# OR install with Homebrew:
brew install --cask docker

# JDK 17 (Eclipse Temurin)
brew install --cask temurin@17

# jq
brew install jq

# Node.js (LTS)
brew install node

# Newman Postman CLI
npm install -g newman

# Optional: mosquitto_sub for verifying the data stream
brew install mosquitto
```

Verify your setup:
```bash
docker --version          # Docker version 24+
java -version             # openjdk 17+
node --version            # v18+
newman --version          # 6+
jq --version              # jq-1.6+
```

---

### Linux (Ubuntu / Debian)

```bash
# System packages
sudo apt-get update
sudo apt-get install -y git curl jq

# Docker Engine + Compose plugin
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker

# JDK 17 (Eclipse Temurin)
sudo apt-get install -y wget apt-transport-https gnupg
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb $(. /etc/os-release; echo $VERSION_CODENAME) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update
sudo apt-get install -y temurin-17-jdk

# Node.js (LTS via NodeSource)
curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash -
sudo apt-get install -y nodejs

# Newman
npm install -g newman

# Optional: mosquitto client tools
sudo apt-get install -y mosquitto-clients
```

---

### Windows (WSL 2)

1. Install **WSL 2** and an Ubuntu distribution from the Microsoft Store.
2. Install **Docker Desktop for Windows** and enable the WSL 2 backend in Settings → Resources → WSL Integration.
3. Open an Ubuntu WSL terminal and follow the [Linux instructions](#linux-ubuntu--debian) above.

> **Note:** All shell scripts in this repository use `bash`. Run them inside the WSL terminal, not in PowerShell or CMD.

---

## 3. Clone the Repository

The two optional companion services are bundled inside this repository, so a single clone is enough. Clone wherever you keep your projects:

```bash
git clone https://github.com/factory-x-contributions/mxport-orion-deployments.git
cd mxport-orion-deployments
```

The repository layout:
```
mxport-orion-deployments/
├── extensions/, launchers/, deployment/, ...          ← core (always needed)
├── industrial-edc-wss-client/                         ← optional (WSS mode, see Section 7)
└── industrial-edc-lightweight-pki-tool/               ← optional (TLS MQTT mode, see Section 7)
```

> **All commands in the rest of this guide assume you are in the repository root** (the `mxport-orion-deployments/` directory created by the clone above).

---

## 4. Choose a Run Mode

There are two ways to run the dataspace:

| | **Path A – Docker Compose** | **Path B – Local / Host** |
|---|---|---|
| **Who it's for** | First-timers, demos, integration testing | Developers who want fast iteration cycles |
| **Dependencies** | Docker only | Docker (for infra) + JDK 17 |
| **Build step** | Automatic on first run of `./run-dataspace-interactive.sh` | `./gradlew build` before first run |
| **Config files** | `deployment/assets/env/` | `deployment/assets/env_local/` |
| **Service addresses** | Container DNS names (`mosquitto-dynsec`, `identityhub-provider`, …) | All `localhost` with individual port numbers |

> **Recommended for first run:** [Path A – Docker Compose](#5-path-a--docker-compose-recommended-for-first-timers).

---

## 5. Path A – Docker Compose (recommended for first-timers)

All services run as Docker containers. A single script handles everything.

### Step 1 – Build Docker images

The first time (and after any source or config change) you must build the images. This step compiles the Java code inside a build container so you do **not** need a local JDK.

```bash
cd deployment
docker compose -f docker-compose.dataspace.yml build
cd ..   # back to project root
```

> **Tip:** This can take 5–10 minutes on a first run while Gradle downloads dependencies.

### Step 2 – Start the dataspace interactively

```bash
./run-dataspace-interactive.sh
```

The script will ask two questions:

**Question 1 – Startup mode:**
```
1) Start fresh (stop existing containers, start new ones, run seeds)
2) Use existing setup (skip startup, go straight to menu)
```
Choose **1** on your first run.

**Question 2 – MQTT profile:**
```
1) plain  – MQTT on port 1883 (no TLS)
2) tls    – MQTT on port 8883 with certificates
```
Choose **1 (plain)** for a first run. See [Lightweight PKI Tool](#lightweight-pki-tool-tls-mqtt-mode) for TLS setup.

The script then:
1. Stops any leftover containers.
2. Starts all services with `docker compose up`.
3. Waits for each service to become healthy.
4. Runs `seed.sh` (identity, participants, policies).
5. Runs `seed-mqtt.sh` (OPC UA MQTT assets).
6. Opens the **interactive operations menu** (see [Section 8](#8-interactive-transfer-walkthrough)).

### Services started

| Service | URL | Description |
|---|---|---|
| `consumer-connector` | `http://localhost:8081` | Consumer management API |
| `provider-connector-qna` | `http://localhost:8191` | Industrial connector management API |
| `provider-connector-manufacturing` | `http://localhost:8291` | Standard provider management API |
| `provider-catalog-server` | `http://localhost:8091` | Aggregated catalog |
| `identityhub-consumer` | `http://localhost:7082` | Consumer identity hub |
| `identityhub-provider` | `http://localhost:7092` | Provider identity hub |
| `issuer-service` | `http://localhost:10013` | VC issuance admin |
| `opcua-server` | `opc.tcp://localhost:4840` | Sample OPC UA server |
| `mosquitto` | `tcp://localhost:1883` | MQTT broker (plain profile) |
| `nginx` | `http://localhost:9876` | DID document server |

---

## 6. Path B – Local / Host Run (recommended for development)

Each EDC runtime runs directly on your machine as a JVM process. Infrastructure dependencies (Mosquitto, OPC UA server, nginx) still run in Docker.

### Step 1 – Start infrastructure dependencies

```bash
./run-local-dependencies.sh
```

Select **1 (Plain MQTT)** for a first run. The script starts three containers:

| Container | Image | Port |
|---|---|---|
| `mosquitto-dynsec` | `eclipse-mosquitto:2` | `1883` |
| `opcua-server` | `ghcr.io/umati/sample-server:main` | `4840` |
| `nginx` | `nginx` | `9876` |

### Step 2 – Build the project

```bash
./gradlew build
```

> This compiles all extensions and produces fat JARs in each launcher's `build/libs/` directory. Expect 2–5 minutes on a first run.

### Step 3 – Launch the EDC runtimes

Open **seven separate terminal tabs/windows** and start each runtime with the commands below. The configuration is read from `deployment/assets/env_local/`. **Each new terminal must be in the repository root** before running these commands.

> **Tip:** Use `tmux` or a terminal multiplexer to manage multiple sessions conveniently.

#### Terminal 1 – Issuer Service
```bash
java $(grep -v '^#' deployment/assets/env_local/issuerservice.env | grep '=' | sed 's/^/-D/' | tr '\n' ' ') \
  -jar launchers/issuerservice/build/libs/issuerservice.jar
```

#### Terminal 2 – Consumer Identity Hub
```bash
java $(grep -v '^#' deployment/assets/env_local/consumer_identityhub.env | grep '=' | sed 's/^/-D/' | tr '\n' ' ') \
  -jar launchers/identity-hub/build/libs/identity-hub.jar
```

#### Terminal 3 – Provider Identity Hub
```bash
java $(grep -v '^#' deployment/assets/env_local/provider_identityhub.env | grep '=' | sed 's/^/-D/' | tr '\n' ' ') \
  -jar launchers/identity-hub/build/libs/identity-hub.jar
```

#### Terminal 4 – Provider Catalog Server
```bash
java $(grep -v '^#' deployment/assets/env_local/provider_catalogserver.env | grep '=' | sed 's/^/-D/' | tr '\n' ' ') \
  -jar launchers/catalog-server/build/libs/catalog-server.jar
```

#### Terminal 5 – Consumer Connector
```bash
java $(grep -v '^#' deployment/assets/env_local/consumer_connector.env | grep '=' | sed 's/^/-D/' | tr '\n' ' ') \
  -jar launchers/runtime-embedded/build/libs/runtime-embedded.jar
```

#### Terminal 6 – Provider Connector QnA (Industrial Connector)
```bash
java $(grep -v '^#' deployment/assets/env_local/provider_connector_qna.env | grep '=' | sed 's/^/-D/' | tr '\n' ' ') \
  -jar launchers/runtime-embedded/build/libs/runtime-embedded.jar
```

#### Terminal 7 – Provider Connector Manufacturing
```bash
java $(grep -v '^#' deployment/assets/env_local/provider_connector_manufacturing.env | grep '=' | sed 's/^/-D/' | tr '\n' ' ') \
  -jar launchers/runtime-embedded/build/libs/runtime-embedded.jar
```

Wait until each terminal prints a line like:
```
INFO  [main] BaseRuntime - Runtime started in ...ms
```

> **Port reference:** consumer `8080–8085`, provider-qna `8190–8195`, provider-manufacturing `8290–8295`, catalog `8091–8092`, consumer identity hub `7080–7083`, provider identity hub `7090–7093`, issuer `10010–10015`.

### Step 4 – Seed the dataspace

```bash
# Seed base identity, policies, credentials
./seed.sh

# Seed OPC UA / MQTT assets
./seed-mqtt.sh
```

### Step 5 – Run the interactive menu

```bash
./run-dataspace-interactive.sh
```

When prompted for the startup mode, select **2 – Use existing setup**. This skips Docker and seeding and drops you straight into the operations menu.

---

## 7. Optional Companion Services

### WSS Client (Firewall-Traversal Mode)

Use this when the OPC UA server is **behind a firewall** and cannot be reached directly by the connector runtime.

**Setup:**
```bash
cd ./industrial-edc-wss-client

# Follow the README in that subfolder for build and configuration.
# Then connect the client to the provider-qna WebSocket server:
#   wss://localhost:8181/industrial-ws   (or ws:// in plain mode)
```

**Enable in the provider-qna config** (`deployment/assets/env_local/provider_connector_qna.env` for local, or `deployment/assets/env/provider_connector_qna.env` for Docker):
```properties
edc.industrial.connector.wss.enabled=true
edc.industrial.wss.port=8181
edc.industrial.wss.path=/industrial-ws
```

> **Important:** The WSS client must be **connected and registered** before any transfer is initiated. Transfers fail immediately if no client is connected.

---

### Lightweight PKI Tool (TLS MQTT Mode)

Use this to enable mutual-TLS authentication between the MQTT broker and the consumer. The PKI tool acts as a local Certificate Authority (CA) that signs per-transfer consumer certificates on demand.

**Setup:**
```bash
cd ./industrial-edc-lightweight-pki-tool

# Follow the README in that subfolder to start the PKI service.
# By default it listens on http://localhost:5114
```

**Enable TLS in the provider-qna config:**
```properties
edc.industrial.connector.cert.auth.enabled=true
edc.industrial.connector.pki.endpoint.url=http://localhost:5114
edc.industrial.connector.pki.endpoint.key=ff94fd70-7f06-45ed-98af-046abf99600d
edc.opcua.mqtt.broker.url=ssl://localhost:8883
edc.opcua.mqtt.ca.cert.path=deployment/mosquitto-dynsec/certs/ca-chain.cert.pem
edc.opcua.mqtt.admin.cert.path=deployment/mosquitto-dynsec/certs/admin-cert.crt
edc.opcua.mqtt.admin.key.path=deployment/mosquitto-dynsec/certs/admin-cert.key.pem
edc.opcua.mqtt.push.user.cert.path=deployment/mosquitto-dynsec/certs/push-user.crt
edc.opcua.mqtt.push.user.key.path=deployment/mosquitto-dynsec/certs/push-user.key.pem
```

When starting Mosquitto (path B), choose **Option 2 (TLS)** in `run-local-dependencies.sh`. When starting Docker Compose, select profile **tls**.

**Additional transfer steps required (TLS only):**
Use interactive menu options **6** and **7** between contract finalization (step 3) and transfer initiation (step 4):
- Option `6` – Create self-signed certificate for the consumer identity.
- Option `7` – Generate a CSR. The CSR is automatically included in the transfer request body.

---

## 8. Interactive Transfer Walkthrough

Once the system is running and seeded, launch the interactive menu (or continue from the `run-dataspace-interactive.sh` session):

```
================================
Dataspace Interactive Menu
================================
1. Get Asset Catalog
2. Initiate Contract Negotiation
3. Check Contract Negotiation Status
4. Initiate Transfer
5. Get EDR Endpoint
6. Create Self-Signed Certificate     ← TLS only
7. Create CSR (Certificate Signing Request)  ← TLS only
8. Show Current Status
9. Exit
```

### Plain MQTT walkthrough (options 1 → 2 → 3 → 4 → 5)

| Step | Menu option | What happens | Expected result |
|---|---|---|---|
| 1 | **Get Asset Catalog** | Queries `provider-catalog-server`; extracts available Asset IDs and Policy IDs | A list of OPC UA MQTT assets is printed; `ASSET_ID` and `POLICY_ID` are stored in session state |
| 2 | **Initiate Contract Negotiation** | Sends a contract offer from consumer to provider | A `CONTRACT_NEGOTIATION_ID` is returned |
| 3 | **Check Contract Negotiation Status** | Polls until state is `FINALIZED` | `CONTRACT_AGREEMENT_ID` is extracted and stored |
| 4 | **Initiate Transfer** | Submits an `MQTT-PUSH` transfer request; the provider starts reading OPC UA and pushing to MQTT | A `TRANSFER_PROCESS_ID` is returned; transfer state moves to `STARTED` |
| 5 | **Get EDR Endpoint** | Retrieves the EDR from the consumer connector | Prints: MQTT broker URL, topic, username, password |

After step 5, use the credentials to subscribe (see [Section 9](#9-verify-the-data-stream)).

### TLS MQTT walkthrough (options 1 → 2 → 3 → 6 → 7 → 4 → 5)

Same as above, but insert these two steps **after step 3 and before step 4**:

| Step | Menu option | What happens |
|---|---|---|
| 3a | **Create Self-Signed Certificate** | Calls PKI service to generate a consumer identity certificate |
| 3b | **Create CSR** | Generates a Certificate Signing Request; the PEM is stored and will be embedded in the transfer request |

The signed certificate and CA chain are returned in the EDR so the consumer can authenticate to the MQTT broker over mutual TLS.

---

## 9. Verify the Data Stream

After a successful transfer, subscribe to the MQTT topic to confirm OPC UA data is flowing.

```bash
# Plain MQTT (username/password from EDR step 5)
mosquitto_sub \
  -h localhost -p 1883 \
  -u <username> -P <password> \
  -t '<assetId>/#' -v

# TLS MQTT (certificate paths from EDR step 5)
mosquitto_sub \
  -h localhost -p 8883 \
  --cafile deployment/mosquitto-dynsec/certs/ca-chain.cert.pem \
  --cert <consumer-cert.pem> \
  --key  <consumer-key.pem> \
  -t '<assetId>/#' -v
```

You should see JSON messages arriving every ~5 seconds (configurable via `pushInterval`), for example:
```
opcua/MachineA/Temperature {"value":23.4,"unit":"°C","timestamp":"2026-05-07T10:00:00Z"}
opcua/MachineA/Pressure    {"value":1.013,"unit":"bar","timestamp":"2026-05-07T10:00:05Z"}
```

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `docker compose build` fails with network errors | Gradle can't download dependencies | Check internet connectivity; retry with `--no-cache` |
| `./seed.sh` fails with `newman: command not found` | Newman not installed | Run `npm install -g newman` |
| `./seed.sh` fails with curl errors | Runtimes not yet ready | Wait for all 7 JVM terminals to print "Runtime started"; retry |
| Transfer stays in `STARTING` | OPC UA server unreachable | Verify `opcua-server` container is running on port `4840`: `docker ps` |
| Transfer fails with `No CSR provided` | TLS auth enabled but CSR not generated | Use menu option **7** before option **4** |
| WebSocket transfer immediately fails | WSS client not connected | Start `industrial-edc-wss-client` and wait for "registered" log line |
| Mosquitto Dynamic Security error | Admin credentials mismatch | Check `edc.opcua.mqtt.admin.*` in the env file matches `dynamic-security.json` |
| DID resolution failure | nginx not running | Run `docker ps` and verify `nginx` is up on port `9876` |
| `java: command not found` (Path B) | JDK not installed or not on PATH | Follow the [Prerequisites](#2-prerequisites) steps; run `java -version` to confirm |
| Port already in use | Previous instance still running | Run `docker ps` and `lsof -i :<port>` to find and stop conflicting processes |

---

## What's Next?

- **Read the main [README](README.md)** for a deep dive into the extension architecture, all configuration properties, and the MQTT authentication modes.
- **Explore the Postman collections** in `deployment/postman/` – they contain every API call the seed and interactive scripts use.
- **Enable WSS mode** to test the firewall-traversal scenario with the `industrial-edc-wss-client` companion.
- **Enable TLS MQTT** using the `industrial-edc-lightweight-pki-tool` for production-like mutual-TLS broker authentication.

