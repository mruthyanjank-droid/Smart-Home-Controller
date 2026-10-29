# Smart Home Controller with CTF‑Style Dashboard

## Overview

This repository contains a **pure‑Java smart‑home controller** that communicates with simulated devices over TCP sockets, and a **cyber‑security‑inspired web dashboard** that provides a command‑and‑control interface.

- **Controller** – Java server (`ControllerServer`) exposing a custom text protocol **and** a lightweight HTTP/REST API for the dashboard.
- **Devices** – Four simulated devices (Light, Fan, Air‑Conditioner, Smart Lock) launched by `DeviceLauncher`.
- **Dashboard** – Dark, futuristic UI (HTML/CSS/JS) that displays system telemetry, device cards, a terminal and an event monitor.
- **Docker Compose** – One‑container solution (`controller` service) that runs the Java backend **and** serves the static UI. A second container (`devices`) launches the simulated devices and waits for the controller to become healthy.

All components run locally; no external services, databases or cloud resources are required.

---

## Prerequisites

- Docker Engine (>= 20.10)
- Docker Compose (v2) – `docker compose` command
- (Optional) `curl` for quick health‑check testing

---

## Quick Start (Development)

```bash
# 1. Clone the repository (if not already done)
# git clone <repo-url>
# cd "APP project - smart home"

# 2. Copy the example environment file and adjust if needed
cp .env.example .env

# 3. Build and start the stack
docker compose up --build
```

The dashboard will be available at **http://localhost:${CONTROLLER_PORT:-5000}** (default port 5000).

---

## Environment Variables (`.env`)

| Variable | Description | Default |
|----------|-------------|---------|
| `CONTROLLER_PORT` | Port on which `ControllerServer` listens (exposed to host) | `5000` |
| `CONTROLLER_HOST` | Hostname other services use to reach the controller (Docker service name) | `controller` |
| `ENVIRONMENT` | Choose `dev` or `prod` – currently only used for log verbosity | `dev` |

Do **not** commit a filled `.env` file; the repository ships with `.env.example`.

---

## Services

| Service | Role | Key Settings |
|---------|------|--------------|
| **controller** | Runs the Java server, serves the dashboard and REST API | Exposes `${CONTROLLER_PORT}` to host, mounts `./logs` for persistence |
| **devices** | Launches the four simulated devices via `DeviceLauncher` | Depends on `controller` health, uses `CONTROLLER_HOST=controller` |

Both services share a bridge network `smarthome-net`.

---

## Using the Dashboard

- **System Overview** – Top bar shows controller address, connected device count, total commands and uptime.
- **Device Cards** – Click a card or the *OPEN CONTROL PANEL* button to view a dedicated control page for that device.
- **Command Terminal** – Type commands such as `LIGHT ON`, `FAN SPEED 3`, `AC SET 24`, `SMARTLOCK LOCK`, `STATUS ALL`, `LIST` and press **Enter**. Responses from the real Java backend appear in the terminal and update the UI automatically.
- **Event Monitor** – Real‑time feed of controller events, device status changes and command acknowledgements.

All interactions are routed through the actual Java socket backend – no mock data.

---

## Verifying the Stack

After `docker compose up` completes, run a quick health‑check:

```bash
curl -f http://localhost:${CONTROLLER_PORT:-5000}/api/status
```

You should receive a JSON payload with telemetry (connected devices, command counters, etc.).

You can also issue a command via the API:

```bash
curl -X POST -H "Content-Type: application/json" \
     -d '{"command":"LIGHT ON"}' \
     http://localhost:${CONTROLLER_PORT:-5000}/api/command
```

The response will contain the device’s acknowledgment (e.g., `RES-101 OK LIGHT IS ON`).

---

## Logs

Container logs are written to `/app/logs` inside the containers and are mounted to the host directory `./logs`. Inspect them with:

```bash
docker logs smarthome-controller   # controller logs
docker logs smarthome-devices      # device launcher logs
```

---

## Shutdown

```bash
# Stop and remove containers, networks and volumes
docker compose down
```

---

## Troubleshooting

- **Health‑check fails** – Ensure the controller container started correctly (`docker logs smarthome-controller`). Verify that port `5000` is not already in use on the host.
- **Devices not connecting** – Check the `devices` logs for connection errors. The `CONTROLLER_HOST` env var must match the Docker service name (`controller`).
- **UI not loading** – Confirm the container is exposing the correct port and that your browser points to `http://localhost:<port>`.

---

## License

This project is provided for educational purposes. Feel free to modify and extend it for your own assignments or demos.
