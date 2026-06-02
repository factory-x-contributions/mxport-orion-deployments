#!/bin/bash

echo "=========================================="
echo "Mosquitto MQTT Broker Configuration"
echo "=========================================="
echo ""
echo "Select configuration:"
echo "1) Plain MQTT (port 1883 only)"
echo "2) TLS MQTT (ports 1883 and 8883 with SSL/TLS)"
echo ""

read -p "Enter your choice (1 or 2): " choice

case $choice in
    1)
        echo ""
        echo "Starting Mosquitto with plain configuration..."
        docker run -d --name mosquitto-dynsec -p 1883:1883 --restart unless-stopped \
          -v "$PWD"/deployment/mosquitto-dynsec/config:/mosquitto/config \
          -v mosquitto-data:/mosquitto/data \
          --add-host host.docker.internal:host-gateway \
          --add-host localhost:host-gateway \
          eclipse-mosquitto:2
        echo "✓ Mosquitto started on port 1883"
        ;;
    2)
        echo ""
        echo "Starting Mosquitto with TLS configuration..."
        docker run -d --name mosquitto-dynsec -p 1883:1883 -p 8883:8883 --restart unless-stopped \
          -v "$PWD"/deployment/mosquitto-dynsec/config-cert:/mosquitto/config \
          -v "$PWD"/deployment/mosquitto-dynsec/certs:/mosquitto/certs \
          -v mosquitto-data:/mosquitto/data \
          --add-host host.docker.internal:host-gateway \
          --add-host localhost:host-gateway \
          eclipse-mosquitto:2
        echo "✓ Mosquitto started on ports 1883 and 8883 (TLS)"
        ;;
    *)
        echo ""
        echo "Invalid choice. Please run the script again and select 1 or 2."
        exit 1
        ;;
esac

docker run -d --name opcua-server -p 4840:4840 ghcr.io/umati/sample-server:main

docker run -d --name nginx -p 9876:80 --rm \
  -v "$PWD"/deployment/assets/issuer/nginx.conf:/etc/nginx/nginx.conf:ro \
  -v "$PWD"/deployment/assets/issuer/did.docker.json:/var/www/.well-known/did.json:ro \
  nginx