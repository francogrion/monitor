# monitor
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

# Steps to run the server

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
	SERVER_PORT=8080 java -jar target/monitor-1.0-SNAPSHOT.jar
	SERVER_PORT=8081 java -jar target/monitor-1.0-SNAPSHOT.jar
```

Running the test suite also requires a `monitor_test` database (`createdb -O monitor monitor_test`)
for the repository integration tests (`@DataJpaTest` against a real Postgres, not an embedded fake).

# Request to config constant M

POST
endpoint:
http://localhost:8080/config/m/{m}

example: http://localhost:8080/config/m/22

# Request to config constant S

POST
endpoint:
http://localhost:8080/config/s/{s}

# Request to get constant M

GET
endpoint:
http://localhost:8080/config/m

# Request to get constant S

GET
endpoint:
http://localhost:8080/config/s


# Request to send monitor data
POST
endpoint:
http://localhost:8080/monitor/data

body:
```JSON
{
	"sensorId": 2,
	"data": 33.54,
	"timestamp": 20192304123322
}
```
