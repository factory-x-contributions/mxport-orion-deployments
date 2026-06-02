#!/bin/sh
set -e

echo "DEBUG: Starting catalog-server"

ENV_FILE="/app/deployment/assets/env/provider_catalogserver.env"

echo "DEBUG: Looking for env file: ${ENV_FILE}"

# Build JAVA_OPTS from env file
JAVA_OPTS=""
if [ -f "${ENV_FILE}" ]; then
  echo "Using configuration from ${ENV_FILE}"

  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      \#*|'') continue ;;
    esac

    key="${line%%=*}"
    value="${line#*=}"

    if echo "$value" | grep -q '^deployment/'; then
      value="/app/${value}"
    fi

    # Only remove trailing slash if value has more than one character
    if [ ${#value} -gt 1 ]; then
      value="${value%/}"
    fi

    if [ -n "$key" ]; then
      JAVA_OPTS="${JAVA_OPTS} -D${key}=${value}"
    fi
  done < "${ENV_FILE}"

  echo "DEBUG: JAVA_OPTS=${JAVA_OPTS}"
fi

export JAVA_OPTS

cd /app
exec /app/bin/catalog-server
