#!/usr/bin/env bash
set -e

echo "Building Angular frontend..."
cd frontend
npm ci
npm run build

echo "Preparing Spring Boot static resources..."
cd ../backend
rm -rf src/main/resources/static/*
mkdir -p src/main/resources/static

echo "Copying Angular build output..."
cp -r ../frontend/dist/frontend/browser/* src/main/resources/static/

echo "Building Spring Boot JAR..."
mvn clean package -DskipTests

echo "Build completed."
