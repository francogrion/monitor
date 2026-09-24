# monitor
[![CI](https://github.com/francogrion/monitor/actions/workflows/ci.yml/badge.svg)](https://github.com/francogrion/monitor/actions/workflows/ci.yml)

API to process sensor data

There are 4 sensors in a system to mesure a numeric value and send it for further processing.
The system get these values and calculate three parameters
(average, max value and min value), looking for the following anomalies:

● The difference between min and max is greater than a constant 'S' (configurable).

● The average value is greater than a constant 'M' (configurable). 

In the case of detecting any of the previous situations, the console should show an error message with some description.

Important to take in account:

● The sensors send 2 measurements per second (independently and potentially simultaneous).

● The system, by hardware limitations, can only process information just two times each minute.

● The order of the readings must be respected.

● All the messages received and the processing must be logged.

For testing,

a) Write at least two tests validating the functionalities.

b) Should be possible to run a test from the console with random data from each sensor.

PLUS: Allow the system to get messages via HTTP.

# Running with Docker

The quickest way to run everything (Postgres + the service) only requires Docker:
```
	docker compose up --build        # one instance: API on http://localhost:8080, actuator on http://localhost:9090
	APP_PORTS=8080-8081 MANAGEMENT_PORTS=9090-9091 docker compose up --build --scale app=2
	                                 # two instances sharing the DB: API on 8080-8081, actuator on 9090-9091
	docker compose down -v           # stop and delete the database volume
```
Inside the container, logs are JSON (ECS format) for log aggregators; outside Docker they are plain text.
The image can also be built and run on its own, pointing it at any Postgres via the env vars in
[Configuration](#configuration):
```
	docker build -t monitor .
	docker run -p 8080:8080 -p 9090:9090 -e DB_URL=jdbc:postgresql://<host>:5432/monitor monitor
```
The image build skips the tests (they need a real Postgres); run `mvn verify` for them.

# Health checks and metrics

Actuator runs on its own port, `9090` by default (`MANAGEMENT_SERVER_PORT`), separate from the API port
(`8080`, `SERVER_PORT`): only the API port needs to be public, and the actuator port is published only to
the monitoring/orchestration network. `/actuator/*` on the API port returns `404`.

Only these Spring Boot Actuator endpoints are exposed:

| Endpoint | Includes | Use it for |
|---|---|---|
| `/actuator/health` | all components (no details) | general status |
| `/actuator/health/liveness` | the application only | restart the container if it fails |
| `/actuator/health/readiness` | the application and the database | stop sending traffic while it fails |
| `/actuator/prometheus` | all metrics, Prometheus format | scraping by Prometheus |

A database outage makes readiness return `503` while liveness stays `200`: the instance stops receiving
traffic but is not restarted (restarting can't fix the database). The Docker image's `HEALTHCHECK` uses
readiness. With the database down, requests (and readiness) fail after `DB_CONNECTION_TIMEOUT_MS`
(5s by default), so probe timeouts should be longer than that.

Business metrics, besides the JVM/HTTP/DB-pool ones Spring Boot provides:

| Metric (Prometheus name) | Meaning |
|---|---|
| `monitor_readings_received_total` | readings accepted by `POST /api/v1/monitor/data` |
| `monitor_anomalies_total{type="average"\|"difference"}` | anomalies detected, by type |
| `monitor_aggregation_batch_size_{count,sum,max}` | readings aggregated per cycle; `_count` is the number of cycles actually processed |
| `tasks_scheduled_execution_seconds{code_function="processData"}` | duration and outcome of each aggregation run (provided by Spring). It also counts runs skipped because another instance held the lock |

# Steps to run the server without Docker

Requires **JDK 25** and a **PostgreSQL** instance (config constants and pending sensor
readings are persisted there; see [ARCHITECTURE.md](ARCHITECTURE.md) ADR-003).

1. Check-out the code
2. Create the database and role (defaults expected by `application.yml`):
```
	createuser monitor --pwprompt   # password: monitor
	createdb -O monitor monitor
```
   Override the connection via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars if needed.
   Schema is created automatically on startup by Flyway (see `src/main/resources/db/migration`).
3. Execute from console:
```
	mvn clean install
```
4. Execute from console:
```
	mvn spring-boot:run
```
or, after building the jar:
```
	java -jar target/monitor-1.0-SNAPSHOT.jar
```
There is a client to test the server, sending random data from 4 simulated sensors:
```
	mvn exec:java@client
```

Several instances can run in parallel against the same database (e.g. behind a load balancer).
Aggregation runs at :00 and :30 of every minute, and a distributed lock (ShedLock) ensures only one
instance performs it per slot (see [ARCHITECTURE.md](ARCHITECTURE.md) ADR-004):
```
	SERVER_PORT=8080 MANAGEMENT_SERVER_PORT=9090 java -jar target/monitor-1.0-SNAPSHOT.jar
	SERVER_PORT=8081 MANAGEMENT_SERVER_PORT=9091 java -jar target/monitor-1.0-SNAPSHOT.jar
```

Running the test suite also requires a `monitor_test` database (`createdb -O monitor monitor_test`)
for the repository integration tests (`@DataJpaTest` against a real Postgres, not an embedded fake).

# Continuous integration

GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs on every pull request and on
pushes to `master`, with two jobs in parallel:
- **Build and test:** `mvn verify` on JDK 25 against a Postgres 16 service container.
- **Docker image smoke test:** builds the image with `docker compose up --build --wait` (which waits for the
  image's health check), checks readiness on the actuator port and that actuator is not reachable on the API
  port, and calls the API.

Dependabot ([`.github/dependabot.yml`](.github/dependabot.yml)) opens weekly PRs to update Maven
dependencies and the GitHub Actions, which are pinned to commit SHAs. The Docker base images are not
included: their tags already follow the JDK 25 patch releases, and moving to another JDK is a manual,
documented change (see [ARCHITECTURE.md](ARCHITECTURE.md) ADR-010).

# Configuration

Everything is configured through environment variables (defaults in `src/main/resources/application.yml`):

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port of the API |
| `MANAGEMENT_SERVER_PORT` | `9090` | HTTP port of actuator (health probes, Prometheus) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/monitor` | JDBC URL of the Postgres database |
| `DB_USERNAME` | `monitor` | Database user |
| `DB_PASSWORD` | `monitor` | Database password |
| `DB_CONNECTION_TIMEOUT_MS` | `5000` | How long a request waits for a DB connection before failing |
| `MONITOR_DEFAULT_M` | `0` | Initial value of `M`, used until it is set via the API |
| `MONITOR_DEFAULT_S` | `0` | Initial value of `S`, used until it is set via the API |
| `MONITOR_AGGREGATION_CRON` | `0,30 * * * * *` | When aggregation runs (Spring cron, with seconds). Keep it wall-clock aligned so all instances fire in the same slots |
| `MONITOR_LOCK_AT_LEAST_FOR` | `PT20S` | Minimum time the aggregation lock is held (absorbs clock skew between instances) |
| `MONITOR_LOCK_AT_MOST_FOR` | `PT29S` | Maximum time the lock is held if the holder dies mid-run |
| `MONITOR_SCHEDULING_ENABLED` | `true` | Set to `false` to disable scheduled aggregation on an instance |
| `OPENAPI_ENABLED` | `true` | Serve the OpenAPI document at `/v3/api-docs`; `false` returns `404` |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | unset (`ecs` in the Docker image) | JSON log format: `ecs`, `logstash` or `gelf`; unset for plain text |

When changing the cron, keep `MONITOR_LOCK_AT_LEAST_FOR` ≤ `MONITOR_LOCK_AT_MOST_FOR` < interval between slots;
otherwise a slot can be skipped (lock still held) or run twice (lock released too early). The service checks this
at startup and refuses to start, naming the offending property, when it doesn't hold.

`M`/`S` defaults only apply while nothing is persisted: once a value is set via the API it is stored in the
database and always wins, even after a restart with different defaults.

The console test client targets `MONITOR_BASE_URL` (default `http://localhost:8080`):
```
	MONITOR_BASE_URL=http://localhost:8090 mvn exec:java@client
```

# API (v1)

All endpoints live under `/api/v1`. The unversioned paths of earlier versions (`/monitor/data`, `/config/m/{m}`,
...) were removed; see [ARCHITECTURE.md](ARCHITECTURE.md) ADR-008.

The OpenAPI 3.1 specification is committed in [`docs/openapi.yaml`](docs/openapi.yaml) and also served by the
service at `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`, on the API port. Load either into any OpenAPI viewer or
client generator. The committed file is generated from the code, and a test fails when they drift apart; after
changing the API, regenerate it with:
```
	mvn test -Dtest=OpenApiSpecTest -Dopenapi.update=true
```

## Send a sensor reading

`POST /api/v1/monitor/data` → `202 Accepted` (the reading is stored and aggregated in the next cycle)
```JSON
{
	"sensorId": "sensor-2",
	"data": 33.54,
	"timestamp": "2026-09-24T08:12:49.515"
}
```
| Field | Rules |
|---|---|
| `sensorId` | required, 1-64 letters, digits, `.`, `_` or `-` |
| `data` | required, number |
| `timestamp` | required, 1-64 characters of a date-time: digits, letters, `:`, `.`, `+`, `-` |

## Read the constants

`GET /api/v1/config` → `200`
```JSON
{ "m": 25.0, "s": 34.0 }
```

## Update the constants

`PATCH /api/v1/config` → `200` with the resulting constants. Send one or both; a missing one stays unchanged.
```JSON
{ "m": 25, "s": 34 }
```
`s` must be `>= 0` (it is a threshold for max − min, which is never negative).

## Errors

Errors use [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`):
```JSON
{
	"status": 400,
	"title": "Bad Request",
	"detail": "Invalid request content.",
	"instance": "/api/v1/monitor/data",
	"errors": [ { "field": "data", "message": "must not be null" } ]
}
```
| Status | When |
|---|---|
| `400` | invalid body: missing/invalid fields (listed in `errors`), malformed JSON, wrong types |
| `404` | unknown path |
| `415` | body is not `application/json` |
| `503` | the database is unavailable; retry after the `Retry-After` header (seconds) |
| `500` | unexpected error (details are only logged, never returned) |
