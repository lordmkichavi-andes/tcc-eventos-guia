# Entrega — Prueba Técnica Desarrollador Advance (2026-07)

Proceso SEL20260703 · Grupo TCC

## Qué se entrega

Servicio REST en Spring Boot 3.3 / Java 17 que recibe eventos de estado de
guía y los publica en RabbitMQ. Cubre el ejercicio previo: API + publicación a
cola + pruebas + documentación. Todo se ejecuta con un solo comando de Docker.

## Arquitectura de la solución

Solución de alta concurrencia para temporadas pico: la API de ingesta valida y
publica cada evento en el broker y responde de inmediato (`202 Accepted`); los
consumidores procesan en paralelo sin afectar la ingesta.

![Arquitectura de la solución](docs/arquitectura.png)

**Flujo de un evento:**

1. El sistema origen (TMS, transporte, APIs externas) envía
   `POST /api/v1/eventos/guia` con el estado y un `X-Idempotency-Key` opcional.
2. La API valida, enriquece y publica en el exchange durable `guia.eventos`
   con mensajes persistentes.
3. Responde `202 Accepted` de inmediato (patrón *accept-then-process*).
4. Los consumidores actualizan BD/cache y disparan notificaciones; deduplican
   con la idempotency key.
5. Reintentos con backoff exponencial; los fallos persistentes van a la DLQ.

**Garantías del diseño:**

- **No perder eventos**: exchange durable + mensajes persistentes, publisher
  confirms, ack manual en consumidores (solo tras persistir) y Dead Letter
  Queue tras N reintentos.
- **Idempotencia**: la clave viaja dentro del mensaje; el consumidor consulta
  un store (Redis/PostgreSQL) antes de aplicar el cambio. Alternativa: clave
  compuesta `numeroGuia + estado + fechaEvento`.
- **Escalabilidad**: API stateless con escalado horizontal; la cola desacopla
  los picos de ingesta del procesamiento; los consumidores escalan por lag.
- **Observabilidad**: logs estructurados con `eventoId`/`traceId`, métricas de
  ingesta/lag/DLQ, trazas distribuidas y alertas sobre lag y tasa de error.
- **Seguridad**: gateway con JWT/mTLS, rate limiting por cliente, secrets en
  vault y enmascaramiento de datos sensibles en logs.

## Alcance implementado

Este repositorio implementa la **capa de ingesta** del diagrama: recepción,
validación y publicación del evento, empaquetada en Docker. Los consumidores,
la deduplicación por idempotency key y el despliegue en Kubernetes quedan
diseñados (sección anterior y camino a producción) y se discuten en la sesión.

## Cómo ejecutarlo

Solo se necesita Docker; la aplicación se compila dentro de la imagen
(build multi-stage con Maven):

```bash
docker compose up -d --build   # RabbitMQ (:5672, consola :15672) + API (:8080)
```

Ejemplo de uso:

```bash
curl -X POST http://localhost:8080/api/v1/eventos/guia \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: idem-001" \
  -d '{"numeroGuia":"GUIA-123456","estado":"EN_TRANSITO","fechaEvento":"2026-07-23T15:30:00Z","origen":"TMS"}'
# → 202 Accepted con eventoId generado
```

Consola de RabbitMQ: http://localhost:15672 (guest / guest).

Para desarrollo local (requiere Java 17 y Maven):

```bash
docker compose up -d rabbitmq  # solo el broker
mvn spring-boot:run            # API en localhost:8080
```

## API

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/v1/eventos/guia` | Registra y publica un evento de estado (202) |
| GET | `/actuator/health` | Health check |

Configuración por variables de entorno: `RABBITMQ_HOST`, `RABBITMQ_PORT`,
`RABBITMQ_USER`, `RABBITMQ_PASSWORD` (defaults para entorno local).

## Estructura del proyecto

```
src/main/java/com/tcc/eventos/
├── controller/     # REST API y manejo global de errores
├── service/        # Lógica de negocio
├── publisher/      # Publicación a cola (interfaz + implementación RabbitMQ)
├── model/          # Evento de dominio (record inmutable)
├── dto/            # Request/Response del API
└── config/         # Exchange y converter JSON de RabbitMQ
Dockerfile          # Build multi-stage (Maven + JRE 17)
docker-compose.yml  # RabbitMQ + API con healthcheck para el orden de arranque
smoke-test.sh       # Pruebas end-to-end del entorno levantado
docs/arquitectura.png
```

## Decisiones técnicas

| Decisión | Ventaja | Desventaja |
|----------|---------|------------|
| **RabbitMQ** como cola | Simple, maduro, confirma entrega, un equipo pequeño lo opera fácil | Menor throughput que Kafka en picos extremos |
| **HTTP 202 Accepted** | Responde rápido sin bloquear al cliente en picos | La confirmación final del estado viaja por otro canal |
| **Interfaz `EventoGuiaPublisher`** | Broker intercambiable (Kafka, SQS) sin tocar controller ni service | Una capa extra (aceptable para evolución) |
| **Idempotencia en el consumidor** | La ingesta queda sin estado ni latencia extra; la key viaja en el mensaje | El consumidor debe ser idempotente por diseño (entrega at-least-once) |
| **Docker multi-stage** | El evaluador solo necesita Docker; misma imagen para CI/CD | Build inicial más lento (mitigado con cache de capas) |

**RabbitMQ vs Kafka** — criterio de elección:

| Criterio | RabbitMQ | Kafka |
|----------|----------|-------|
| Complejidad operativa | Baja | Media-alta |
| Throughput extremo | Suficiente para el volumen esperado | Superior en millones/min |
| Reprocesamiento histórico | Limitado (DLQ) | Nativo (retención por días) |
| Equipo pequeño | Más fácil de operar | Requiere más expertise |
| **Recomendación inicial** | **Sí** — MVP y validación | Migrar si las métricas lo exigen |

La interfaz de publisher hace que esta decisión sea reversible a bajo costo.

## Pruebas

```bash
mvn test           # 5 pruebas unitarias y de slice web
./smoke-test.sh    # 7 verificaciones end-to-end con el entorno levantado
```

| Prueba | Cubre |
|---|---|
| `EventoGuiaControllerTest` | 202 con payload válido y publicación; 400 con payload inválido y con JSON malformado |
| `EventoGuiaServiceTest` | Construcción del evento y propagación de idempotency key |
| `RabbitConfigTest` | Serialización JSON real del evento hacia RabbitMQ (regresión) |
| `smoke-test.sh` | Health, 202 con y sin idempotency key, 400 por validación y por JSON malformado, y llegada real del mensaje al exchange (API de management de RabbitMQ) |

Dos de las pruebas existen por hallazgos concretos durante la construcción:

- El `RabbitTemplate` por defecto no serializa records de dominio y el
  endpoint fallaba en runtime aunque los tests con mocks pasaban. Se corrigió
  con un `Jackson2JsonMessageConverter` y `RabbitConfigTest` detecta la
  regresión sin necesitar RabbitMQ levantado.
- Un body con JSON malformado respondía 500; lo detectó el smoke test. Se
  corrigió en el manejador global de errores (400) y quedó cubierto por test.

## Camino a producción

1. **Semana 1-2**: MVP (este repo) + contrato OpenAPI + pruebas de carga básicas.
2. **Semana 3-4**: Consumidores de estado y notificaciones + idempotencia en Redis.
3. **Semana 5-6**: CI/CD (GitHub Actions), despliegue en K8s/EKS, observabilidad.
4. **Semana 7+**: Prueba de carga en staging, runbooks, capacitación al equipo.

```
commit → build + tests → imagen Docker → deploy staging → smoke tests → deploy prod (aprobación manual)
```

La imagen que construye `docker compose up --build` es la misma que
alimentaría este pipeline.

## Requisitos

- Docker (única dependencia para ejecutar todo)
- Java 17+ y Maven 3.8+ (solo para desarrollo local y correr los tests)
