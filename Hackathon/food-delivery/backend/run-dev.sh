#!/usr/bin/env bash
# Local development: seeds demo data and uses the dev profile. Export DB_PASSWORD first (see application-dev.properties).
cd "$(dirname "$0")" && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
