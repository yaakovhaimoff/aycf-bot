# FlightFinder MCP Server

Automates flight searches for **Wizz Air's All You Can Fly (AYCF)** membership program. Uses Selenium WebDriver to interact with the Wizz Air site and find available flights — exposed as an MCP server that AI assistants (e.g. Claude) can call directly.

## Features

- **Direct flight search** — check availability for a specific route and date
- **Multi-day search** — concurrently searches the next 4 days for a route
- **Connection search** — finds one-stop itineraries when no direct flights exist
- **Route discovery** — parses the official Wizz Air AYCF PDF to know which routes exist before hitting the browser
- **MCP server** — exposes all search tools over HTTP/SSE so AI assistants can call them
- **Prometheus metrics** — every search method is timed and exported at `/actuator/prometheus`

## Requirements

- Java 17
- Chrome browser (managed automatically by Selenium)
- A valid Wizz Air AYCF account

## Quick Start

```bash
# Set credentials
export ADMIN_USER=your@email.com
export ADMIN_PASSWORD=yourpassword

# Build and run
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`.

## Docker

```bash
# Build
./mvnw clean package
docker build -t flightfinder .

# Run
docker run -p 8080:8080 \
  -e ADMIN_USER=your@email.com \
  -e ADMIN_PASSWORD=yourpassword \
  flightfinder
```

> The Docker image bundles Chrome. The `HEADLESS=true` env var is set by default.

## Configuration

All settings live in `src/main/resources/application.properties`.

| Property                    | Default                  | Description                              |
|-----------------------------|--------------------------|------------------------------------------|
| `app.admin.user`            | *(env `ADMIN_USER`)*     | Wizz Air login email                     |
| `app.admin.password`        | *(env `ADMIN_PASSWORD`)* | Wizz Air login password                  |
| `server.address`            | `127.0.0.1`              | Bind address (localhost-only by default) |
| `spring.ai.mcp.server.type` | `SYNC`                   | MCP transport mode                       |

## MCP Server

The app runs an [MCP](https://modelcontextprotocol.io) server at `http://localhost:8080/sse`. AI assistants with MCP support can call four tools:

| Tool                      | Description                           |
|---------------------------|---------------------------------------|
| `searchDirectFlight`      | Search a specific route and date      |
| `searchNextDaysFlights`   | Search the next 4 days concurrently   |
| `searchConnectionFlights` | Find one-stop itineraries             |
| `listAvailableRoutes`     | List all routes from the Wizz Air PDF |

**Claude / OpenClaw setup** (`~/.vscode/mcp.json`):
```json
{
  "servers": {
    "flight-finder": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

## Architecture

```
┌─────────────────────────────────────────────┐
│              Spring Boot App                │
│                                             │
│  FlightMcpTools           — MCP tools       │
│                                             │
│  SearchFlightsService                       │
│  ├── direct search (1 thread)               │
│  ├── next-days search (4 threads)           │
│  └── connection search (4 threads)          │
│                                             │
│  Selenium WebDriver (Chrome, headless)      │
│  ├── LoginPage                              │
│  └── SearchFlightsPage                      │
│                                             │
│  FlightsFromPdfService — parses route PDF   │
└─────────────────────────────────────────────┘
```

**Session-bound WebDriver**: each authenticated HTTP session gets its own Chrome instance, cleaned up on logout.

**Multi-threaded searches**: connection and multi-day searches spin up to 4 Chrome instances in parallel via `ExecutorService`, each logging in independently with stored credentials.

## Build Commands

```bash
./mvnw clean package          # Build JAR
./mvnw spring-boot:run        # Run
./mvnw test                   # Run all tests
./mvnw test -Dtest=FlightFinderApplicationTests  # Single test class
```

## Metrics

Prometheus metrics are exported at `http://localhost:8080/actuator/prometheus`.

Key timers:
- `flightFinder.searchDirectFlight`
- `flightFinder.searchFlightsWithConnections`
- `flightFinder.searchNextDayFlights`
