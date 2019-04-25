# monitor
API to process sensor data


Existen 4 sensores en un sistema que miden un valor numérico y deben enviarlo para su
procesamiento. El sistema de monitoreo, toma estos valores y calcula tres parámetros:
promedio, valor máximo y valor mínimo buscando alguna de las siguientes anomalías:

● La diferencia entre el valor mínimo y máximo recibido sea mayor a una constante S
(configurable)

● El valor promedio sea superior a una constante M (configurable)
En caso de detectar alguna de las situaciones mencionadas en los puntos anteriores, debe
mostrar por pantalla un mensaje de error indicando esta situación.

Es importante tener en cuenta que:
● Los sensores envían 2 mediciones por segundo (en forma independiente y
potencialmente simultánea).

● El sistema de procesamiento, por limitaciones de hardware, sólo puede procesar
información 2 veces por minuto.

● Se debe respetar el orden de ingreso de los mensajes al sistema de monitoreo.

● Todos los mensajes recibidos deben ser loggeados asi como también registrar
información al momento de su procesamiento.

En Java o C#, desarrolle un programa que se ejecute desde consola y que modele este
sistema.
Para probarlo,

a) Escribir al menos dos tests que validen la funcionalidad alguna de las funcionalidades
requeridas.

b) Desde la consola se deberá poder ejecutar un caso en el los 4 sensores generen
información aleatoria que será procesada por el sistema de monitoreo.

PLUS: Permitir que el sistema de monitoreo reciba los mensajes mediante HTTP


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
