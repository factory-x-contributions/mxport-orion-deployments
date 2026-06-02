plugins {
    `java-library`
}

dependencies {
    implementation(project(":extensions:common-spi"))
    implementation(project(":extensions:industrial-connector-local"))
    implementation(libs.edc.boot)
    implementation(libs.edc.core.runtime)

    // BouncyCastle for PEM certificate/key parsing
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")

    // MQTT Client - Eclipse Paho v3
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // Transfer SPI for DataFlow support
    implementation("org.eclipse.edc:transfer-spi:0.9.0")
    implementation("org.eclipse.edc:web-spi:0.9.0")
    runtimeOnly("org.eclipse.edc:jetty-core:0.9.0")
}