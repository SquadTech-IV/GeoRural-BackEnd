#!/bin/sh
set -e

if [ -n "$ORACLE_WALLET_BASE64" ]; then
  mkdir -p /app/wallet
  echo "$ORACLE_WALLET_BASE64" | base64 -d > /tmp/wallet.zip
  unzip -o /tmp/wallet.zip -d /app/wallet
  rm -f /tmp/wallet.zip
fi

exec java -jar /app/app.jar --server.port=${PORT:-8080}