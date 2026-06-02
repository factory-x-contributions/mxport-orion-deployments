// extensions/industrial-connector-local/build.gradle.kts
plugins {
    `java-library`
}

dependencies {
    implementation(project(":extensions:common-spi"))
    implementation(libs.edc.boot)
    implementation(libs.edc.core.runtime)

    // OPC UA client - Milo SDK (self-contained, no opcua extension dependency)
    implementation(libs.milo.sdk.client)

    // Transfer SPI for DataFlow support
    implementation("org.eclipse.edc:transfer-spi:0.17.0")
    implementation("org.eclipse.edc:web-spi:0.17.0")
    runtimeOnly("org.eclipse.edc:jetty-core:0.17.0")

    // JAX-RS for REST API endpoints
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    // MQTT Client - Eclipse Paho v3
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // JSON processing
    implementation(libs.jackson.datatype.jakarta.jsonp)

    // BouncyCastle for PEM certificate/key parsing
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
