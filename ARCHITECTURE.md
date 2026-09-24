# Arquitectura

Este documento describe la arquitectura del servicio `monitor`, su evolución y las decisiones tomadas en el camino, junto con el razonamiento detrás de cada una. Se actualiza cada vez que se toma una decisión de arquitectura relevante.

## Arquitectura actual (as-is)

- Aplicación Java 8 monolítica, empaquetada como jar, sin framework de aplicación (usa Spark Java como servidor HTTP embebido).
- Un único proceso, sin persistencia real: el estado (`M`, `S`, buffer de lecturas) vive en singletons en memoria (`DataBaseService`, `MonitorHandler`).
- La agregación periódica se hace con `java.util.Timer` corriendo dentro del mismo proceso.
- No hay containerización, configuración externa, ni observabilidad más allá de logs por consola (SLF4J).

Limitaciones principales: no escala horizontalmente (el estado es local a la instancia), pierde todo el estado ante un reinicio, y no sigue las convenciones típicas de un microservicio desplegable de forma independiente en un entorno distribuido.

## Arquitectura objetivo (to-be)

Migración incremental (ver plan de fases en `PLANNING.md`) hacia un microservicio:

- Framework de aplicación: Spring Boot.
- Estado externalizado en una base de datos real.
- Agregación de lecturas coordinada entre instancias (para permitir múltiples réplicas).
- Configuración externa (variables de entorno / `application.yml`).
- Empaquetado como imagen de contenedor, con health checks.
- Observabilidad (métricas, logging estructurado).

## Decisiones de arquitectura (log)

Cada decisión se documenta con: contexto, decisión, alternativas consideradas y consecuencias. Se numeran secuencialmente (ADR-XXX).

---

### ADR-001: Migrar el stack a Spring Boot 4.1.1 sobre Java 25

**Estado:** Aceptada

**Contexto:**
El servicio actual está construido a mano sobre Spark Java y Java 8, sin inyección de dependencias, sin soporte nativo para configuración externa, testing de integración, ni observabilidad (health checks, métricas). Estas carencias son justamente las que impiden clasificar al servicio como un microservicio productivo.

**Decisión:**
Migrar la base de código a **Spring Boot 4.1.1** corriendo sobre **Java 25**.

**Por qué:**
- Spring Boot provee de forma estándar lo que hoy se implementa a mano o falta directamente: inyección de dependencias (reemplaza los singletons manuales tipo `DataBaseService.getInstance()`), configuración externa vía `application.yml`/variables de entorno, Actuator para health checks y métricas, y un ecosistema de testing maduro (`@SpringBootTest`, `MockMvc`) que facilita seguir TDD tanto a nivel unitario como de integración.
- Java 25 es la versión LTS más reciente al momento de esta decisión, lo que da soporte de largo plazo y acceso a mejoras de la JVM (rendimiento, virtual threads/Project Loom ya estables) relevantes para un servicio que necesita procesar mensajes concurrentes de múltiples sensores.
- Alinea el proyecto con el stack estándar de la industria para microservicios Java, facilitando además la futura integración con Spring Data (persistencia), Spring Cloud (si se necesita service discovery/config server más adelante), y Spring Kafka/AMQP (para la cola de agregación de la Fase 2 del plan).

**Alternativas consideradas:**
- Mantener Spark Java y agregar las piezas faltantes (config externa, DI, health checks) de forma manual — descartada por reinventar funcionalidad que Spring Boot da out-of-the-box, con mayor costo de mantenimiento.
- Quarkus/Micronaut (frameworks más livianos, mejor arranque en frío) — no se eligieron porque el objetivo no es optimizar cold-start (no se apunta a serverless), y Spring Boot tiene mayor adopción y documentación disponibles.

**Consecuencias:**
- Requiere reescribir los `controllers`/`handlers` actuales como componentes Spring (`@RestController`, `@Service`).
- Los tests existentes (`MathUtilsTest`, `JsonUtilsTest`) se portan al nuevo stack de testing.
- El `pom.xml` se reemplaza por el BOM de Spring Boot 4.1.1 y sus starters correspondientes.

---

### ADR-002: Detalles de implementación de la migración a Spring Boot (Fase 0)

**Estado:** Aceptada

**Contexto:**
Al ejecutar la migración definida en ADR-001 surgieron varias decisiones concretas no anticipadas, principalmente por cambios de Spring Boot 4 / Spring Framework 7 respecto a versiones anteriores.

**Decisiones:**

1. **Jackson 3 en vez de Jackson 2.** Spring Boot 4.1.1 trae `spring-boot-starter-jackson`, que resuelve `tools.jackson.core:jackson-databind` (Jackson 3, con `ObjectMapper` bajo el paquete `tools.jackson.databind`) en lugar del clásico `com.fasterxml.jackson.databind`. Se ajustó `JsonUtils` a la nueva API. Las anotaciones (`@JsonProperty`, etc.) siguen viviendo en `com.fasterxml.jackson.annotation` (ese módulo no cambió de coordenadas), por lo que `SensorData` no requirió cambios.

2. **Clase `@SpringBootApplication` en el paquete raíz `com`.** Se ubicó `MonitorApplication` en `com` (no en `com.main`) para que el component scan por defecto cubra `com.controller`, `com.service`, `com.domain` y `com.utils`, y para que los slices de test (`@WebMvcTest`) puedan encontrarla buscando hacia arriba desde el paquete del test — Spring solo busca en paquetes ancestros, no en paquetes hermanos.

3. **`spring-boot-starter-webmvc-test` como dependencia de test explícita.** En Spring Boot 4, `@WebMvcTest` se modularizó fuera de `spring-boot-starter-test` (vive ahora en `org.springframework.boot.webmvc.test.autoconfigure`, distribuido en el starter `spring-boot-starter-webmvc-test`). Hubo que agregarlo aparte.

4. **`java.util.Timer` reemplazado por `@Scheduled(fixedRate = 30000)`** en `MonitorService`, habilitado con `@EnableScheduling` en la aplicación. Es la forma idiomática de Spring de expresar un job periódico, y deja el mecanismo listo para evolucionar en la Fase 2 (agregación coordinada entre instancias) sin acoplarse a `Timer`.

5. **`ConfigService` y `MonitorService` como `@Service` en memoria.** Reemplazan a `DataBaseService`/`ConfigHandler`/`MonitorHandler` (singletons manuales). La inyección de dependencias de Spring hace innecesario el patrón singleton a mano, pero el estado sigue siendo en memoria: la externalización a una base de datos real queda para la Fase 1, sin cambios de diseño adicionales.

6. **Extracción de `AnomalyChecker`.** La lógica `avg > M` / `diff > S` se movió a una clase utilitaria pura (paquete-privada, sin dependencias de logging), para poder testearla de forma directa y mantener `MonitorService` enfocado en orquestar lectura, agregación y logging.

7. **`RestClient` migrado de Apache HttpClient a `java.net.http.HttpClient`** (incluido en el JDK), ya que Apache HttpClient dejó de ser una dependencia del proyecto al remover Spark. Evita reintroducir una dependencia externa solo para el cliente de prueba de consola.

**Consecuencias:**
- 40 tests (unitarios y de integración con `MockMvc`) cubren `AnomalyChecker`, `ConfigService`, `MonitorService`, ambos controllers, `MathUtils` y `JsonUtils`.
- Verificado manualmente end-to-end: `mvn clean verify` empaqueta el jar, `java -jar target/monitor-1.0-SNAPSHOT.jar` levanta el servidor, y los endpoints de config y de ingesta de datos responden correctamente, incluyendo la detección de ambas anomalías bajo carga concurrente de 4 sensores simulados.

---

*(Los próximos ADRs — persistencia, mecanismo de agregación coordinada, containerización, etc. — se agregan a medida que se van decidiendo, siguiendo las fases definidas en `PLANNING.md`.)*
