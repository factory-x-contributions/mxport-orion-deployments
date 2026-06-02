package org.eclipse.edc.common.spi.security.mosquitto;

import org.eclipse.edc.common.spi.security.SecurityRequest;

/**
 * Security provisioning request for Mosquitto Dynamic Security plugin.
 */
public class MosquittoSecurityRequest extends SecurityRequest {
    /**
     * Common name from the client certificate or username. Used for role assigning in the dynamic security plugin.
     */
    private String userName;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * MQTT role name the client is allowed to access.
     */
    private String roleName;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    /**
     * MQTT topic or topic pattern the client is allowed to access.
     * Example: factory/companyA/telemetry/#
     */
    private String topic;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}