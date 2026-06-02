package edc.industrial.connector.wss.client.mqtt;

/**
 * Service for publishing data to an MQTT broker.
 */
public interface MqttPublisherService {

    /**
     * Publishes a payload to the specified topic.
     *
     * @param topic   the MQTT topic
     * @param payload the message payload bytes
     * @param qos     quality of service level (0, 1, or 2)
     * @throws Exception if publishing fails
     */
    void publish(String topic, byte[] payload, int qos) throws Exception;

    /**
     * Closes the MQTT connection and releases resources.
     */
    void close();
}

