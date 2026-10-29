# Multi-stage Dockerfile for Smart Home Controller & Simulated Devices
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app
COPY src/ ./src/
COPY tests/ ./tests/
COPY web/ ./web/

RUN mkdir -p bin && \
    javac -d bin src/smarthome/util/*.java \
                 src/smarthome/protocol/*.java \
                 src/smarthome/device/*.java \
                 src/smarthome/server/*.java \
                 src/smarthome/client/*.java \
                 src/smarthome/launcher/*.java \
                 tests/smarthome/*.java

# Runtime Image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
# Install curl for reliable container healthchecks
RUN apk add --no-cache curl

COPY --from=builder /app/bin ./bin
COPY web/ ./web/
RUN mkdir -p logs

EXPOSE 5000

# Default entrypoint starts Controller Server
ENTRYPOINT ["java", "-cp", "bin", "smarthome.server.ControllerServer"]
