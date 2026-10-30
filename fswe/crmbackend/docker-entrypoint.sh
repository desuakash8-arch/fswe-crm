#!/bin/sh
set -eu

# Render mounts the downloaded Aiven CA as a secret file. This trust store is
# used by MySQL only, so HTTPS services retain Java's normal public root CAs.
if [ -n "${DB_SSL_CA:-}" ]; then
    if [ ! -r "$DB_SSL_CA" ]; then
        echo "DB_SSL_CA is not readable. Add aiven-ca.pem in Render Secret Files." >&2
        exit 1
    fi
    trust_dir=$(mktemp -d /tmp/crm-trust.XXXXXX)
    if ! keytool -importcert -noprompt -alias aiven -file "$DB_SSL_CA" \
        -keystore "$trust_dir/mysql.p12" -storetype PKCS12 -storepass changeit >/dev/null 2>&1; then
        echo "Could not import the Aiven CA certificate. Check the aiven-ca.pem secret file." >&2
        exit 1
    fi
    chmod 600 "$trust_dir/mysql.p12"
    export DB_TRUSTSTORE_URL="file:$trust_dir/mysql.p12"
fi

exec java -jar /app/app.jar "$@"
