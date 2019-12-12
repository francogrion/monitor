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

1. Check-out the code
2. Go to the downloads path
3. Execute from console:
```
	mvn clean install
```
4. Execut from console:
```
	mvn exec:java@server
```
There is a client to test the server:
```
	mvn exec:java@client
```

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
