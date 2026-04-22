#!/usr/bin/env bash
# Runs the full backend test suite in Docker (no IDE needed).
# Prerequisites: Docker Desktop running. Backend server does NOT need to be up —
# tests use H2 in-memory, not the running Postgres.
#
# Usage:
#   bash run-backend-tests.sh                      # all backend tests
#   bash run-backend-tests.sh BookingControllerTest   # just one test class

set -e

TEST_FILTER=""
if [ -n "$1" ]; then
    TEST_FILTER="--tests com.racketmatch.api.$1"
fi

docker run --rm \
    -v /Users/maciek/Desktop/r/backend:/app \
    -v racketmatch-gradle-cache:/root/.gradle \
    -w /app \
    eclipse-temurin:21-jdk \
    ./gradlew test $TEST_FILTER
