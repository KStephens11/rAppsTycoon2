#!/bin/bash
# Build and run all services (no cache to pick up code changes)

echo "Stopping existing containers..."
docker compose down

echo "Building frontend with no cache..."
docker compose build --no-cache frontend

echo "Building other services..."
docker compose build backend event-generator

echo "Starting all services..."
docker compose up -d --force-recreate

echo ""
echo "Services:"
echo "  Frontend: http://localhost:3001"
echo "  Backend:  http://localhost:8080"
echo "  MySQL:    localhost:3307"
