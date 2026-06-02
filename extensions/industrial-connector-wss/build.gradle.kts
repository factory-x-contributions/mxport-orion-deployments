plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.boot)
    implementation(libs.edc.core.runtime)

    // Runtime metamodel for @Inject annotation
    implementation("org.eclipse.edc:runtime-metamodel:0.9.0")

    // Transfer and control plane dependencies
    implementation("org.eclipse.edc:control-plane-spi:0.9.0")
    implementation("org.eclipse.edc:transfer-spi:0.9.0")

    // Common SPI for TransferFlowService
    implementation(project(":extensions:common-spi"))

    // Web and WebSocket support
    implementation("org.eclipse.edc:web-spi:0.9.0")
    runtimeOnly("org.eclipse.edc:jetty-core:0.9.0")

    // Jetty Server
    implementation("org.eclipse.jetty:jetty-server:12.0.7")

    // WebSocket - Jetty WebSocket API
    implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-api:12.0.7")
    implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-server:12.0.7")

    // JSON processing
    implementation(libs.jackson.datatype.jakarta.jsonp)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")

    // Security - for WSS/TLS support
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
