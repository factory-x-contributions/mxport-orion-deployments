#!/bin/bash
# Wrapper script to load environment variables from env_file and convert to Java properties

# Source all environment variables into the shell
if [ -f "/etc/environment" ]; then
    set -a
    source /etc/environment
    set +a
fi

# Build JVM options from environment variables that start with edc. or web.
JVM_OPTS=""
while IFS='=' read -r name value; do
    if [[ "$name" =~ ^(edc|web)\. ]]; then
        # Convert to Java system property format
        JVM_OPTS="$JVM_OPTS -D$name=$value"
    fi
done < <(env)

# Also add LOG_LEVEL if set
if [ -n "$LOG_LEVEL" ]; then
    JVM_OPTS="$JVM_OPTS -DLOG_LEVEL=$LOG_LEVEL"
fi

# Execute the application with the JVM options
exec ./bin/identity-hub --log-level=${LOG_LEVEL:-debug} $JVM_OPTS "$@"

