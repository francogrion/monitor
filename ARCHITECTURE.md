# Arquitectura

Este documento describe la arquitectura del servicio `monitor`, su evolución y las decisiones tomadas en el camino, junto con el razonamiento detrás de cada una. Se actualiza cada vez que se toma una decisión de arquitectura relevante.

## Estado actual

- Spring Boot 4.1.1 sobre Java 25 (ADR-001, ADR-002).
- Estado (`M`, `S` y lecturas pendientes de agregación) persistido en PostgreSQL vía Spring Data JPA, esquema versionado con Flyway (ADR-003).
- Stateless a nivel de instancia: pueden correr N réplicas contra la misma base; la agregación periódica se coordina con ShedLock para que corra una sola vez por slot en todo el cluster (ADR-004).
- Toda la configuración (puerto, base, defaults de `M`/`S`, cron y duraciones del lock) se toma de variables de entorno, con defaults en `application.yml` (ADR-005).
- Pendiente: containerización, observabilidad y hardening de la API (Fases 4–6 de `PLANNING.md`).

## Punto de partida (arquitectura original)

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

### ADR-003: PostgreSQL + Spring Data JPA + Flyway para el estado persistente (Fase 1)

**Estado:** Aceptada

**Contexto:**
`ConfigService` y `MonitorService` guardaban `M`, `S` y el buffer de lecturas en memoria (beans `@Service` de Spring, pero sin persistencia real). Un reinicio del proceso perdía toda esa información, y no había forma de que múltiples instancias compartieran ese estado — el objetivo de la Fase 1 (ver `PLANNING.md`) era resolver esto.

**Decisión:**
Usar **PostgreSQL** como base de datos, con **Spring Data JPA** (Hibernate) como capa de acceso y **Flyway** para versionar el esquema, tanto para la configuración (`M`/`S`) como para el buffer de lecturas de sensores pendientes de agregación.

**Por qué:**
- Un único motor de datos cubre los dos casos de uso de esta fase (config de baja frecuencia y una cola de lecturas de alta frecuencia relativa, pero de volumen trivial: ~8 inserts/seg). Introducir dos motores distintos (p. ej. Postgres + Redis) hubiera sido complejidad operativa innecesaria para el volumen real del sistema.
- Da garantías transaccionales: `processData()` lee y borra las lecturas ya agregadas dentro de la misma transacción (`@Transactional`), evitando perder o duplicar lecturas que lleguen concurrentemente durante el ciclo de agregación.
- Es el camino más directo hacia la Fase 2 (agregación coordinada entre instancias): una tabla es un mecanismo de coordinación válido (con `SELECT ... FOR UPDATE` o similar) sin introducir aún un message broker.
- Flyway deja el esquema versionado y reproducible (`src/main/resources/db/migration/V1__init.sql`) en vez de depender de `ddl-auto=update`, que puede generar cambios de esquema silenciosos. `spring.jpa.hibernate.ddl-auto` se configuró en `validate`: Hibernate valida contra el esquema real pero nunca lo modifica.
- Las credenciales de conexión se externalizaron desde el día uno vía variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, con defaults locales), adelantando parte de la Fase 3 para no introducir secretos hardcodeados en el código nuevo.

**Cómo se probó (decisión de testing):**
En vez de testear contra una base embebida distinta a la de producción (H2), se usa un **Postgres real local** tanto para desarrollo como para los tests de integración (`@DataJpaTest` con `@AutoConfigureTestDatabase(replace = Replace.NONE)` apuntando a una base `monitor_test` separada). Esto evita el clásico problema de "pasa en los tests con H2 pero falla en producción con Postgres" por diferencias de dialecto SQL. El costo es que correr los tests requiere un Postgres accesible (documentado en `README.md`); se evalúa Testcontainers como alternativa cuando el proyecto tenga Docker disponible en su pipeline de CI (Fase 4).

**Alternativas consideradas:**
- **H2 en memoria para tests, Postgres en producción**: descartado por el riesgo de falsos positivos en tests (dialectos SQL distintos).
- **Redis** para el buffer de lecturas (por ser una cola de alta frecuencia): descartado en esta fase por volumen trivial de datos y por evitar sumar un segundo motor de persistencia antes de necesitarlo.
- **`ddl-auto=update`**: descartado en favor de migraciones versionadas con Flyway, más seguras para un entorno con múltiples desarrolladores/instancias.

**Consecuencias:**
- Nuevas dependencias: `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` (Flyway 10+ requiere el módulo de base de datos específico por separado; no viene incluido en el starter).
- Nuevas entidades (`ConfigEntity`, `SensorReadingEntity`) y repositorios (`ConfigRepository`, `SensorReadingRepository`) en `com.domain` / `com.repository`.
- `MonitorService.processData()` ahora borra explícitamente por lista de IDs (`deleteAllInBatch(pending)`) en lugar de vaciar todo el buffer, para no perder lecturas insertadas concurrentemente durante el ciclo de agregación.
- Verificado manualmente: se mató el proceso con `kill -9` con lecturas y configuración pendientes de procesar, y al reiniciar el servidor `M`/`S` se recuperaron intactos y las lecturas pendientes se agregaron correctamente en el primer ciclo posterior al reinicio.
- 48 tests en total (15 nuevos: 6 de repositorios contra Postgres real, 9 de servicios re-testeados con mocks de los repositorios).

---

### ADR-004: Agregación coordinada entre instancias con ShedLock + trigger alineado al reloj (Fase 2)

**Estado:** Aceptada. Reemplaza el punto 4 de ADR-002 (`@Scheduled(fixedRate = 30000)`).

**Contexto:**
Con el estado ya en Postgres (ADR-003), el servicio podía correr en varias instancias, pero cada una tenía su propio `@Scheduled` disparando `processData()`. Eso generaba dos problemas:
1. **Procesamiento duplicado o concurrente:** varias instancias podían agregar el mismo lote al mismo tiempo, loguear anomalías duplicadas y competir al borrar las mismas filas.
2. **Violación de la restricción de hardware** ("solo se puede procesar 2 veces por minuto"): con `fixedRate`, cada instancia arranca su contador en el momento en que bootea, así que los disparos no están alineados. Aun con un lock que evite ejecuciones simultáneas, con 2 instancias podía haber 3 agregaciones en un mismo minuto (p. ej. t=0s, t=26s, t=56s).

**Decisión:**
- **ShedLock** (`shedlock-spring` + `shedlock-provider-jdbc-template`) como lock distribuido para el job, usando la misma base Postgres como backend (tabla `shedlock`, migración `V2__shedlock.sql`). `processData()` lleva `@SchedulerLock(name = "processSensorData", lockAtLeastFor = "PT20S", lockAtMostFor = "PT29S")`.
- **Trigger alineado al reloj:** `@Scheduled(cron = "0,30 * * * * *")` en lugar de `fixedRate`. Todas las instancias disparan en los segundos :00 y :30; exactamente una gana el lock en cada slot y las demás se saltean ese ciclo. Resultado: como máximo 2 agregaciones por minuto en todo el cluster, sin importar cuántas instancias haya.
- **Tiempos del lock:** `lockAtLeastFor = 20s` absorbe diferencias de reloj entre instancias (una instancia con el reloj algunos segundos atrasado sigue encontrando el lock tomado en el mismo slot). `lockAtMostFor = 29s` garantiza que si la instancia que tiene el lock muere a mitad del proceso, el lock expira antes del próximo slot y otra instancia lo toma.
- **`usingDbTime()`:** los timestamps del lock se calculan con el reloj de Postgres, no con el de cada instancia, así que el clock skew entre instancias no rompe la exclusión mutua.
- **Orden de los advisors:** `@EnableSchedulerLock(order = HIGHEST_PRECEDENCE)` hace que el lock envuelva a `@Transactional`: lock → begin → procesar → commit → unlock. Así el lock nunca se libera antes de que el borrado de las lecturas procesadas quede commiteado. (ShedLock además escribe el lock en su propia transacción `REQUIRES_NEW`, por lo que es visible para otras instancias de inmediato aunque el método sea transaccional; lo verificamos inspeccionando el bytecode del `JdbcTemplateStorageAccessor`.)
- `@EnableScheduling` se movió de `MonitorApplication` a `SchedulingConfig`, condicionado a `monitor.scheduling.enabled` (default `true`), para que los tests de integración puedan invocar `processData()` de forma determinística sin que el scheduler dispare en paralelo.

**Por qué ShedLock y no otra opción:**
- **Cola de mensajes (Kafka/RabbitMQ):** resuelve el problema, pero agrega un componente de infraestructura nuevo para un volumen trivial (~8 lecturas/seg). Queda como opción si más adelante se necesita procesamiento por streaming o más throughput.
- **Advisory locks de Postgres a mano (`pg_try_advisory_lock`):** funciona, pero es reimplementar lo que ShedLock ya da, atado a Postgres y con más código propio a mantener y testear.
- **Líder elegido (Spring Integration leader election, Kubernetes lease):** más potente pero más complejo; y el despliegue en Kubernetes todavía no está definido (Fase 4).
- **ShedLock** reutiliza la base que ya tenemos, es un par de anotaciones, y es el patrón estándar en Spring para "un `@Scheduled` que corre una sola vez en el cluster".

**Gotcha encontrado durante el desarrollo:**
ShedLock inserta la fila de cada lock una sola vez y cachea en memoria que existe; de ahí en adelante solo hace `UPDATE ... WHERE lock_until <= now`. Borrar filas de `shedlock` mientras hay instancias corriendo hace que esas instancias nunca vuelvan a obtener el lock (hasta reiniciarlas). Los tests "expiran" el lock (`UPDATE ... SET lock_until = '2000-01-01'`) en lugar de borrarlo. **Operativamente: nunca borrar filas de `shedlock` con el servicio corriendo.**

**Cómo se probó:**
- `SchedulerLockConfigTest` (contra Postgres real): el lock se concede si está libre, se rechaza una segunda adquisición mientras está tomado, se vuelve a conceder tras liberarlo, y de dos hilos que compiten al mismo tiempo exactamente uno lo obtiene.
- `MonitorServiceLockingTest` (`@SpringBootTest` contra Postgres real): si otra "instancia" tiene el lock `processSensorData`, `processData()` se saltea el ciclo y no toca las lecturas pendientes; si está libre, las procesa. Se verificó por mutación que el test falla si se quita `@SchedulerLock`.
- Manual: dos instancias (puertos 8080 y 8081) contra la misma base, recibiendo 257 lecturas repartidas entre ambas durante 70s. La agregación ocurrió exactamente una vez por slot (09:04:00, 09:04:30, 09:05:00), siempre en una sola instancia; la otra disparaba en los mismos instantes y se salteaba. Luego se mató con `kill -9` a la instancia que venía ganando el lock y la otra tomó el relevo en el slot siguiente, procesando también las lecturas que la instancia muerta había recibido (ya estaban persistidas).

**Consecuencias:**
- El primer ciclo de agregación ya no ocurre al arrancar la aplicación, sino en el próximo :00 o :30 del reloj.
- Una instancia puede quedar sin agregar nunca mientras otra gane siempre el lock; es esperado (el trabajo se hace una vez por slot, no importa dónde).
- 54 tests en total (6 nuevos).

---

### ADR-005: Configuración externa vía variables de entorno (Fase 3)

**Estado:** Aceptada

**Contexto:**
Tras las Fases 1 y 2, el puerto y la conexión a la base ya se leían de variables de entorno, pero seguían hardcodeados: los valores iniciales de `M`/`S` (0 implícito), el cron de agregación, las duraciones del lock y la URL del cliente de prueba de consola.

**Decisión:**
- **Variables de entorno como única interfaz de configuración**, con defaults declarados en `application.yml` mediante placeholders (`${MONITOR_DEFAULT_M:0}`). Es el mecanismo estándar para contenedores (factor III de 12-factor) y deja todas las perillas visibles en un solo archivo. La tabla completa está en `README.md`.
- **Defaults de `M`/`S`** como `@ConfigurationProperties` tipado (`ConfigDefaults`, prefijo `monitor.defaults`) inyectado en `ConfigService`. Se usan solo mientras no hay configuración persistida. Una vez que `M` o `S` se setean por API, el valor persistido siempre gana, incluso si la instancia reinicia con otros defaults: la base es la fuente de verdad del estado (ADR-003), y cambiar un env var no debería pisar silenciosamente un valor que alguien configuró explícitamente.
- **Primera escritura:** cuando se setea `M` por primera vez, la fila nueva guarda `S` con su default configurado, y viceversa. Antes se creaba con `0`, lo que hubiera hecho que configurar `M` "reseteara" el `S` por default.
- **Cron y duraciones del lock** como placeholders directamente en las anotaciones (`@Scheduled(cron = "${monitor.aggregation.cron}")`, `@SchedulerLock(lockAtLeastFor = "${...}")`), porque las anotaciones no pueden leer un bean de configuración. Defaults: `0,30 * * * * *`, `PT20S`, `PT29S` (los de ADR-004).
- `monitor.scheduling.enabled` (ADR-004) también se expone como `MONITOR_SCHEDULING_ENABLED`, lo que permite correr instancias que solo reciben datos, sin agregar.
- El cliente de consola (`RestClient`) lee `MONITOR_BASE_URL`.

**Alternativas consideradas:**
- **Spring Cloud Config / servidor de configuración centralizado:** agrega un componente más para un servicio con una decena de parámetros. Los env vars se integran directo con Docker/Kubernetes (ConfigMaps/Secrets) en la Fase 4.
- **Validar al arranque el invariante `lockAtLeastFor ≤ lockAtMostFor < intervalo del cron`:** calcular el intervalo de una expresión cron arbitraria no es trivial (puede no ser uniforme). Por ahora el invariante se documenta en `application.yml` y en el `README`. ShedLock ya rechaza en tiempo de ejecución `lockAtLeastFor > lockAtMostFor`.

**Cómo se probó:**
- `ConfigServiceTest`: con defaults distintos de cero (22/34), se devuelven cuando no hay nada persistido, los valores persistidos ganan, y la primera escritura de uno conserva el default del otro.
- `ConfigDefaultsBindingTest` (`ApplicationContextRunner`, sin base): las propiedades se bindean al record, y quedan en `0` si no se configuran.
- `AggregationConfigurationTest` (`@SpringBootTest` contra Postgres real): con propiedades custom, el cron registrado en el scheduler es el configurado, y el lock escrito en `shedlock` dura exactamente `lock-at-least-for` (45s en el test). Primero falló contra el código hardcodeado (cron fijo, 20s).
- Manual: app en el puerto 8090 con `MONITOR_DEFAULT_M=22`, `MONITOR_DEFAULT_S=40`, cron cada 10s y lock de 5s/9s. `GET /config/m` y `GET /config/s` devolvieron 22/40; tras `POST /config/m/30`, la fila quedó con `m=30, s=40`. El `RestClient` con `MONITOR_BASE_URL=http://localhost:8090` envió 172 lecturas, la agregación corrió cada 10s y el lock quedó tomado 5s.

**Consecuencias:**
- 59 tests en total (5 nuevos).
- Una configuración inconsistente de cron/lock no se detecta al arrancar; queda como riesgo operativo documentado.

---

*(Los próximos ADRs — containerización, observabilidad, etc. — se agregan a medida que se van decidiendo, siguiendo las fases definidas en `PLANNING.md`.)*
