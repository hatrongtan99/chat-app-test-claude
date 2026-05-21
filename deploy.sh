#!/usr/bin/env bash
set -euo pipefail

echo "==> [1/3] Building JAR (skip tests)..."
./mvnw.cmd clean package -DskipTests

ENV_FILES="--env-file .env"
if [ -f ".env.local" ]; then
    ENV_FILES="$ENV_FILES --env-file .env.local"
    echo "    (.env.local detected - overrides applied)"
fi

echo "==> [2/3] Building Docker image..."
docker compose $ENV_FILES build

echo "==> [3/3] Starting stack..."
docker compose $ENV_FILES up -d

echo ""
echo "Done. Services:"
docker compose $ENV_FILES ps
