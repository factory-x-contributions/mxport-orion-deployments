plugins {
    java
    idea
    id("com.gradleup.shadow") version "9.3.1"
}

group = "edc.industrial.connector"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    // Eclipse Paho is not in Maven Central – use the Eclipse repo
    maven { url = uri("https://repo.eclipse.org/content/repositories/paho-releases/") }
}

dependencies {
    // WebSocket client
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // Eclipse Milo OPC-UA SDK
    implementation("org.eclipse.milo:sdk-client:0.6.12")
    implementation("org.eclipse.milo:stack-client:0.6.12")

    // Eclipse Paho MQTT v3
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // Jackson JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // BouncyCastle (PEM / TLS)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // dotenv – reads *.env files
    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.3")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "edc.industrial.connector.wss.client.WssOpcUaClientApp"
    }
}

tasks.shadowJar {
    archiveClassifier = "standalone"
    manifest {
        attributes["Main-Class"] = "edc.industrial.connector.wss.client.WssOpcUaClientApp"
    }
    // Exclude signature files that break fat-jar loading
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
}

// Make the default 'build' task produce the fat JAR
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Convenience task: build + run the fat JAR
tasks.register<JavaExec>("runLocal") {
    group = "application"
    description = "Build and run the application with client.local.env"
    dependsOn(tasks.shadowJar)
    classpath = files(tasks.shadowJar.get().archiveFile)
    mainClass = "edc.industrial.connector.wss.client.WssOpcUaClientApp"
    environment("WSS_CLIENT_ENV_FILE", "client.local.env")
}

// Fast dev task: uses the normal compile classpath (no fat-jar rebuild needed).
// Use this from IntelliJ: Gradle tool window → Tasks → application → runDev
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Run the application in dev mode (full classpath, no shadow JAR)"
    dependsOn(tasks.compileJava, tasks.processResources)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "edc.industrial.connector.wss.client.WssOpcUaClientApp"
    workingDir = projectDir
    environment("WSS_CLIENT_ENV_FILE", "client.local.env")
    // Allow the JVM to find Netty's native libs when running from the IDE
    jvmArgs("-Dio.netty.tryReflectionSetAccessible=true")
}

