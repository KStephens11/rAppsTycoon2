#!/bin/bash
# Build and run all services (no cache to pick up code changes)

echo "Stopping existing containers..."
docker compose down

echo "Building and starting all services (fresh build)..."
docker compose up -d --build --force-recreate

echo ""
echo "Services:"
echo "  Frontend: http://localhost:3001"
echo "  Backend:  http://localhost:8080"
echo "  MySQL:    localhost:3307"
