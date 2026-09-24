# Planning

Este documento centraliza las especificaciones funcionales del sistema y el plan de trabajo para evolucionarlo hacia un microservicio productivo. Se actualiza a medida que se van definiendo y completando tareas.

## Especificación funcional (base)

- 4 sensores envían 2 mediciones por segundo cada uno, de forma independiente y potencialmente simultánea.
- El sistema solo puede procesar información 2 veces por minuto (limitación de hardware simulada).
- El orden de las lecturas debe respetarse.
- Cada ciclo de procesamiento calcula: promedio, máximo y mínimo de las lecturas acumuladas.
- Anomalías a detectar y loguear como error:
  - `max - min > S`
  - `promedio > M`
- `M` y `S` son constantes configurables en runtime vía API.
- Todos los mensajes recibidos y el procesamiento deben quedar logueados.
- Debe poder recibirse datos vía HTTP.

## Metodología de trabajo: TDD

Todo el desarrollo nuevo (y las migraciones sobre código existente) se hace siguiendo **Test-Driven Development**:

1. **Red** — Escribir un test que exprese el comportamiento esperado y que falle (porque el código todavía no existe o no lo soporta).
2. **Green** — Escribir el mínimo código necesario para que el test pase.
3. **Refactor** — Limpiar el diseño (nombres, duplicación, responsabilidades) sin romper los tests, y solo entonces avanzar al siguiente ciclo.

Reglas adicionales:
- No se escribe código de producción sin un test que lo motive primero.
- Los tests son la documentación viva del comportamiento esperado; si el comportamiento cambia, el test cambia primero.
- Se priorizan tests unitarios rápidos (JUnit + Mockito) y se agregan tests de integración para los endpoints REST (ej. `@SpringBootTest` + `MockMvc`/`WebTestClient`) sobre los flujos críticos (ingestión de datos, cálculo de anomalías, configuración de `M`/`S`).
- Cobertura no es el objetivo en sí mismo, pero cada bug encontrado se corrige agregando primero el test que lo reproduce.

## Roadmap hacia microservicio productivo

Basado en el análisis de arquitectura (ver `ARCHITECTURE.md`). Estado: `[ ]` pendiente, `[~]` en progreso, `[x]` hecho.

### Fase 0 — Migración de stack
- [x] Migrar el proyecto a Spring Boot 4.1.1 sobre Java 25
- [x] Reescribir controllers/handlers actuales como componentes Spring (`@RestController`, `@Service`)
- [x] Portar tests existentes (`MathUtilsTest`, `JsonUtilsTest`) al nuevo stack

### Fase 1 — Estado externalizado
- [x] Reemplazar `DataBaseService` (singleton en memoria) por persistencia real (PostgreSQL vía Spring Data JPA, ver ADR-003)
- [x] Persistir `M` y `S`
- [x] Persistir o encolar las lecturas de sensores pendientes de agregación

### Fase 2 — Diseño stateless / escalabilidad horizontal
- [x] Reemplazar el `Timer` en memoria por un mecanismo de agregación coordinado entre instancias (job coordinado con ShedLock + cron alineado al reloj, ver ADR-004)
- [x] Verificar que múltiples instancias del servicio puedan correr en paralelo sin duplicar ni perder datos

### Fase 3 — Configuración externa
- [ ] Externalizar puerto, valores iniciales de `M`/`S`, y demás configuración vía `application.yml` / variables de entorno

### Fase 4 — Empaquetado y despliegue
- [ ] Dockerfile
- [ ] Health check (`/actuator/health`)

### Fase 5 — Observabilidad y resiliencia
- [ ] Logging estructurado
- [ ] Métricas (Micrometer / Prometheus)
- [ ] Timeouts/reintentos si se agregan integraciones externas

### Fase 6 — API
- [ ] Versionado de endpoints
- [ ] Validación de input y manejo de errores más específico

## Notas
- Cada decisión técnica relevante (por qué Spring Boot 4.1.1, por qué tal base de datos, etc.) se documenta en `ARCHITECTURE.md`, no acá.
