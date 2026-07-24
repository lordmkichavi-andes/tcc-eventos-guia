# Bitácora — Preparación Prueba Técnica TCC (Desarrollador ADVANCE)

Notas de trabajo de la sesión: qué se hizo, qué se encontró y cómo se resolvió cada
punto. Este documento es para tu preparación personal (no es el entregable formal —
para eso están `README.md` y `ARQUITECTURA.md`).

## 1. De qué se trata la prueba

**Proceso:** SEL20260703 — Prueba Técnica Desarrollador ADVANCE, Grupo TCC.

**Contexto de negocio:** TCC es logística/mensajería nacional. En temporada pico se
dispara el volumen de eventos de guías (estados, novedades, notificaciones). Se pide
una solución de alta concurrencia que capture eventos, actualice estado casi en
tiempo real y notifique al cliente, **sin perder eventos** y sin degradar la
operación, operable por un equipo pequeño.

**Entregable del ejercicio previo (máx. 2h):** una API REST que reciba un evento de
estado de guía y lo publique en una cola/flujo de eventos, con una prueba y
documentación breve. Es la base para la conversación técnica en vivo — **no se
evalúa la extensión, se evalúan las decisiones**.

**Estructura de la sesión (70 min):**

| Eje | Tiempo | Foco |
|---|---|---|
| 1. Arquitectura | 25 min | Diseño de la solución, ventajas/desventajas, camino a producción |
| 2. Desarrollo y calidad | 20 min | Bajar a código, pruebas, calidad, uso de IA — ejercicio en vivo |
| 3. Liderazgo | 15 min | Primeros 90 días, mentoría, manejo de desacuerdos |
| Cierre | 10 min | Reflexión: "el mejor proyecto en 18 meses" |

**Entrega:** mínimo 12h antes de la sustentación, a `jhrey@tcc.com.co`, asunto
`Prueba Técnica Desarrador Advance (2026-07)`.

## 2. Qué se construyó (ya existía en el repo)

Servicio Spring Boot 3.3 / Java 17 que expone:

```
POST /api/v1/eventos/guia   → recibe el evento, lo valida y lo publica en RabbitMQ
GET  /actuator/health        → health check
GET  /actuator/info          → info (vacío, sin build-info configurado)
```

Estructura: `controller/`, `service/`, `publisher/` (interfaz + impl RabbitMQ),
`model/`, `dto/`, `config/`. Documentación de arquitectura y decisiones ya cubierta
en `README.md` y `ARQUITECTURA.md`.

## 3. Cómo se levantó el entorno

macOS con **Colima** (no Docker Desktop) como backend de Docker.

```bash
# 1. Arrancar Colima (backend de Docker en macOS)
colima start

# 2. Levantar RabbitMQ (imagen rabbitmq:3.13-management)
docker compose up -d
docker compose ps          # confirmar que el contenedor está "Up"

# 3. Compilar y correr la API
mvn spring-boot:run
```

- API: `http://localhost:8080`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ management UI: `http://localhost:15672` — usuario `guest` / password `guest`
  (definidos en `docker-compose.yml`, variables `RABBITMQ_DEFAULT_USER` /
  `RABBITMQ_DEFAULT_PASS`)

## 4. Punto por punto: qué se probó y cómo se resolvió

### 4.1 Endpoint principal — primera prueba

```bash
curl -X POST http://localhost:8080/api/v1/eventos/guia \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-001" \
  -d '{
    "numeroGuia": "GUIA-001",
    "estado": "EN_TRANSITO",
    "fechaEvento": "2026-07-23T17:00:00Z",
    "origen": "APP_MOVIL",
    "metadata": {"ciudad": "Bogota"}
  }'
```

**Resultado inicial:** `HTTP 500`

```json
{"detalle":"SimpleMessageConverter only supports String, byte[] and Serializable payloads, received: com.tcc.eventos.model.EventoGuia","error":"Error interno"}
```

### 4.2 Bug encontrado: falta `MessageConverter` en RabbitMQ

**Causa raíz:** el `RabbitTemplate` autoconfigurado por Spring Boot usa por defecto
`SimpleMessageConverter`, que solo serializa `String`, `byte[]` o `Serializable`.
`EventoGuia` es un `record` plano (no `Serializable`), así que cada publicación
fallaba en tiempo de ejecución — el endpoint nunca lograba publicar nada.

**Solución** (`src/main/java/com/tcc/eventos/config/RabbitConfig.java`): agregar un
bean `Jackson2JsonMessageConverter`. Spring Boot detecta automáticamente cualquier
bean `MessageConverter` en el contexto y lo inyecta en el `RabbitTemplate`
autoconfigurado.

```java
@Bean
MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

**Verificación:** se reinició la app y se repitió la misma request →
`HTTP 202 Accepted` con el evento publicado correctamente.

```bash
curl -X POST http://localhost:8080/api/v1/eventos/guia \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-002" \
  -d '{"numeroGuia":"GUIA-001","estado":"EN_TRANSITO","fechaEvento":"2026-07-23T17:00:00Z","origen":"APP_MOVIL","metadata":{"ciudad":"Bogota"}}'
# → 202 {"eventoId":"...","numeroGuia":"GUIA-001","estado":"EN_TRANSITO","publicadoEn":"...","mensaje":"Evento publicado correctamente"}
```

### 4.3 Actuator — health / info

```bash
curl http://localhost:8080/actuator/health   # → 200 {"status":"UP"}
curl http://localhost:8080/actuator/info     # → 200 {}  (sin build-info configurado, esperado)
```

Sin hallazgos. Exposición limitada a `health,info` en `application.yml`
(`management.endpoints.web.exposure.include`), correcto por seguridad — no se
expone todo el actuator por defecto.

### 4.4 Validación — body inválido

```bash
curl -X POST http://localhost:8080/api/v1/eventos/guia \
  -H "Content-Type: application/json" \
  -d '{"numeroGuia":"","estado":"","origen":"APP_MOVIL"}'
```

**Resultado:** `HTTP 400`

```json
{"detalle":"estado: no debe estar vacío, numeroGuia: no debe estar vacío, fechaEvento: no debe ser nulo","error":"Solicitud inválida"}
```

Correcto — `@Valid` + Bean Validation en `EventoGuiaRequest` + `GlobalExceptionHandler`
funcionan como se espera, con mensajes por campo.

### 4.5 Idempotencia — ¿bug o decisión de diseño?

**Prueba:** se envió la misma `X-Idempotency-Key` dos veces con el mismo payload.

**Resultado:** cada llamada generó un `eventoId` distinto y volvió a publicar el
evento — es decir, **no hay deduplicación** en la API.

**Análisis:** en un primer momento se marcó como posible bug, pero al revisar
`README.md` (tabla "Decisiones técnicas") y `ARQUITECTURA.md` (sección
"Idempotencia") se confirmó que es una **decisión de arquitectura intencional y ya
documentada**: la API (capa de ingesta) solo propaga el `X-Idempotency-Key` dentro
del mensaje publicado; la deduplicación real ocurre en el **consumidor**, que
consulta un store (Redis/PostgreSQL) antes de aplicar el cambio de estado. Esto es
consistente con el alcance explícito del ejercicio previo ("solo la capa de
ingesta", `ARQUITECTURA.md` línea 125-127).

**No se modificó código.** Este es justo el tipo de punto para explicar
ventajas/desventajas en vivo durante el Eje 1: por qué la idempotencia no vive en el
productor (evita estado/latencia adicional en el hot path de ingesta, se apoya en
que RabbitMQ ya garantiza entrega at-least-once) y sus trade-offs (el consumidor
debe ser idempotente por diseño; dos publicaciones del mismo evento son válidas y
esperadas).

### 4.6 Sin header de idempotencia (es opcional)

```bash
curl -X POST http://localhost:8080/api/v1/eventos/guia \
  -H "Content-Type: application/json" \
  -d '{"numeroGuia":"GUIA-SIN-IDEM","estado":"PENDIENTE","fechaEvento":"2026-07-23T18:05:00Z","origen":"APP_MOVIL"}'
# → 202, funciona igual (header es @RequestHeader(required = false))
```

### 4.7 Suite de tests existente

```bash
mvn test
# Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Cobertura: `EventoGuiaControllerTest` (2 tests: 202 + publish llamado, 400 en
inválido) y `EventoGuiaServiceTest` (1 test).

**Gap detectado:** ambos tests mockean `EventoGuiaPublisher`
(`@MockBean private EventoGuiaPublisher publisher`), así que **nunca ejercitan la
serialización real hacia RabbitMQ** — por eso los 3 tests pasaban aunque el bug del
`MessageConverter` (punto 4.2) rompiera el endpoint en runtime real. Ningún test
cubría `RabbitEventoGuiaPublisher` ni el bean `MessageConverter` de `RabbitConfig`.

### 4.8 Test de regresión agregado

**Archivo:** `src/test/java/com/tcc/eventos/config/RabbitConfigTest.java`

Test unitario, sin Spring context ni Docker: instancia el
`Jackson2JsonMessageConverter` directamente desde `RabbitConfig`, serializa un
`EventoGuia` real a `Message` y lo deserializa de vuelta, verificando igualdad.
Si alguien vuelve a quitar el bean `jsonMessageConverter()`, este test falla en
segundos sin necesitar RabbitMQ levantado.

```bash
mvn test -Dtest=RabbitConfigTest
```

*(Pendiente de correr y confirmar en esta sesión — ejecutar el comando de arriba
para verificar que compila y pasa antes de dar por cerrado este punto.)*

## 5. Checklist antes de enviar

- [x] `colima start` + `docker compose up -d` — RabbitMQ arriba
- [x] `mvn spring-boot:run` — API arranca sin errores
- [x] Bug de `MessageConverter` corregido y verificado con curl
- [x] Validación de errores probada (400)
- [x] Actuator health/info probado
- [x] Idempotencia revisada — confirmado que es decisión documentada, no bug
- [ ] `mvn test` completo (incluyendo `RabbitConfigTest`) en verde
- [ ] Revisar `README.md` / `ARQUITECTURA.md` una vez más por consistencia
- [ ] Enviar a `jhrey@tcc.com.co` con asunto `Prueba Técnica Desarrador Advance (2026-07)`, mínimo 12h antes

## 6. Comandos de referencia rápida

```bash
colima start                          # backend Docker en macOS
docker compose up -d                  # levantar RabbitMQ
docker compose ps                     # ver estado del contenedor
mvn spring-boot:run                   # correr la API (localhost:8080)
mvn test                              # correr toda la suite
mvn test -Dtest=RabbitConfigTest      # correr solo el test de regresión nuevo
docker compose down                   # apagar RabbitMQ al terminar
colima stop                           # apagar la VM de Colima
```