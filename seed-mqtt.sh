#!/bin/bash

## This script seeds OPC UA via MQTT assets to the MVD dataspace
## It assumes the dataspace is already running and seeded with the main seed.sh script

set -e

# OPC UA Server Configuration
OPC_SERVER_URL="${OPC_SERVER_URL:-opc.tcp://localhost:4840}"
OPC_SERVER_USERNAME="${OPC_SERVER_USERNAME:-}"
OPC_SERVER_PASSWORD="${OPC_SERVER_PASSWORD:-}"

# MQTT Broker Configuration
MQTT_BROKER_URL="${MQTT_BROKER_URL:-tcp://localhost:1883}"

echo "Seeding OPC UA via MQTT assets to dataspace..."
echo "OPC UA Server URL: $OPC_SERVER_URL"
echo "MQTT Broker URL: $MQTT_BROKER_URL"

## Seed OPC UA MQTT assets to "provider-qna"
echo "Seeding Provider QnA with OPC UA MQTT assets..."
newman run \
  --folder "Seed OPC UA MQTT Provider QnA" \
  --env-var "HOST=http://127.0.0.1:8191" \
  --env-var "OPC_SERVER_URL=$OPC_SERVER_URL" \
  --env-var "OPC_SERVER_USERNAME=$OPC_SERVER_USERNAME" \
  --env-var "OPC_SERVER_PASSWORD=$OPC_SERVER_PASSWORD" \
  --env-var "MQTT_BROKER_URL=$MQTT_BROKER_URL" \
  ./deployment/postman/MVD-OPCUAMQTT.postman_collection.json > /dev/null

echo "✓ Provider QnA seeded successfully!"

## Seed OPC UA MQTT assets to "provider-manufacturing"
echo "Seeding Provider Manufacturing with OPC UA MQTT assets..."
newman run \
  --folder "Seed OPC UA MQTT Provider Manufacturing" \
  --env-var "HOST=http://127.0.0.1:8291" \
  --env-var "OPC_SERVER_URL=$OPC_SERVER_URL" \
  --env-var "OPC_SERVER_USERNAME=$OPC_SERVER_USERNAME" \
  --env-var "OPC_SERVER_PASSWORD=$OPC_SERVER_PASSWORD" \
  --env-var "MQTT_BROKER_URL=$MQTT_BROKER_URL" \
  ./deployment/postman/MVD-OPCUAMQTT.postman_collection.json > /dev/null

echo "✓ Provider Manufacturing seeded successfully!"

## Seed linked OPC UA MQTT assets to Catalog Server
echo "Seeding Catalog Server with linked OPC UA MQTT assets..."
newman run \
  --folder "Seed OPC UA MQTT Catalog Server" \
  --env-var "HOST=http://127.0.0.1:8091" \
  --env-var "PROVIDER_QNA_DSP_URL=http://localhost:8192" \
  --env-var "PROVIDER_MF_DSP_URL=http://localhost:8292" \
  --env-var "MQTT_BROKER_URL=$MQTT_BROKER_URL" \
  ./deployment/postman/MVD-OPCUAMQTT.postman_collection.json > /dev/null

echo "✓ Catalog Server seeded successfully!"

echo ""
echo "=========================================="
echo "OPC UA via MQTT assets seeded successfully!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Start MQTT broker (if not already running):"
echo "   docker-compose -f deployment/mqtt/docker-compose-mqtt.yml up -d"
echo ""
echo "2. Monitor MQTT topics:"
echo "   mosquitto_sub -h localhost -p 1883 -t '#' -v"
echo ""
echo "3. Access MQTT broker dashboard:"
echo "   http://localhost:18083 (admin/public)"
echo ""
echo "4. Initiate transfers from consumer to start data flow:"
echo "   - Use Postman collection: MVD-OPCUAMQTT.postman_collection.json"
echo "   - Or use EDC consumer management API"
echo ""

