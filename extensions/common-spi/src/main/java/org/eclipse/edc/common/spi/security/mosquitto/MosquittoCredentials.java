package org.eclipse.edc.common.spi.security.mosquitto;

import org.eclipse.edc.common.spi.security.Credentials;

/**
 * Represents credentials created for a Mosquitto user.
 */
public class MosquittoCredentials extends Credentials {
    private final String username;
    private final String password;
    private final String roleName;
    private final String topic;

    public MosquittoCredentials(String username, String password, String roleName, String topic) {
        this.username = username;
        this.password = password;
        this.roleName = roleName;
        this.topic = topic;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getTopic() {
        return topic;
    }
}
