# Multi-stage build for Runtime Embedded launcher (used by connectors)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy Gradle wrapper early to cache gradle setup
COPY gradlew gradlew
COPY gradle gradle
COPY gradle.properties gradle.properties

# Copy build configuration
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts

# Copy sources - do this in stages to maximize cache
COPY launchers launchers
COPY extensions extensions
COPY resources resources
COPY deployment deployment
COPY tests tests

# Build with retries and extended timeouts
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    --mount=type=cache,target=/workspace/.gradle,sharing=locked \
    set -ex && \
    ./gradlew --no-daemon \
              --stacktrace \
              -Dorg.gradle.internal.http.socketTimeout=300000 \
              -Dorg.gradle.internal.http.connectionTimeout=300000 \
              -Dorg.gradle.internal.network.retries=5 \
              :launchers:runtime-embedded:installDist || \
    (echo "First attempt failed, retrying..." && \
     sleep 10 && \
     ./gradlew --no-daemon \
               --stacktrace \
               -Dorg.gradle.internal.http.socketTimeout=300000 \
               -Dorg.gradle.internal.http.connectionTimeout=300000 \
               -Dorg.gradle.internal.network.retries=5 \
               :launchers:runtime-embedded:installDist)

# Runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV LOG_LEVEL=debug

# Copy distribution
COPY --from=build /workspace/launchers/runtime-embedded/build/install/runtime-embedded /app

# Copy wrapper script
COPY launchers/runtime-embedded/run.sh /app/run.sh
COPY deployment /app/deployment
RUN chmod +x /app/run.sh && cat /app/run.sh | head -1

# Working dir /app to preserve relative paths like deployment/assets/...
ENTRYPOINT ["/app/run.sh"]
