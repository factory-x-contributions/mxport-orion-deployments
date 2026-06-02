# =============================================================
# Dockerfile – Industrial Connector WSS Client
#
# Supports: linux/amd64 (x86-64) and linux/arm64 (Apple Silicon)
#
# PRE-REQUISITE: build the fat JAR on the host first:
#   ./gradlew shadowJar
#
# Then build the image:
#   docker build -t edc/industrial-connector-wss-client:latest .
# =============================================================

FROM eclipse-temurin:17-jre

# Non-root user for security
RUN groupadd -r opcua && useradd -r -g opcua opcua

WORKDIR /app

# Copy the standalone fat JAR produced by `./gradlew shadowJar`
COPY build/libs/*-standalone.jar app.jar

# Default env file location inside the container.
# Mount client.docker.env to /app/client.env at runtime.
ENV WSS_CLIENT_ENV_FILE=/app/client.env

# Log directory
RUN mkdir -p /app/logs && chown -R opcua:opcua /app

USER opcua

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
