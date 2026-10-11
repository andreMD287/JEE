# Taller 1 Jakarta EE: Sistema de Gestión de Pedidos

**Pontificia Universidad Javeriana · Arquitectura de Software · Taller No. 1 Jakarta Enterprise Edition (Datos)**

Backend empresarial para un comercio electrónico que gestiona **productos, inventario, pedidos y pagos**. El proceso de compra se coordina con el patrón **SAGA por orquestación**, con operaciones compensatorias cuando una etapa falla, y aplica **JPA** para la persistencia, **JTA** para las transacciones y **CQRS** para separar escrituras de lecturas.

---

## Contenido

1. [Entregables](#1-entregables)
2. [Inicio rápido](#2-inicio-rápido)
3. [Requisitos](#3-requisitos)
4. [Ejecución con Docker](#4-ejecución-con-docker)
5. [Cómo probar el sistema](#5-cómo-probar-el-sistema)
6. [Arquitectura](#6-arquitectura)
7. [Patrones y conceptos aplicados](#7-patrones-y-conceptos-aplicados)
8. [Requisitos del taller y su implementación](#8-requisitos-del-taller-y-su-implementación)
9. [Modelo de datos y estados](#9-modelo-de-datos-y-estados)
10. [Referencia de la API REST](#10-referencia-de-la-api-rest)
11. [Manejo de errores](#11-manejo-de-errores)
12. [Configuración](#12-configuración)
13. [Decisiones de diseño y alcance](#13-decisiones-de-diseño-y-alcance)
14. [Solución de problemas](#14-solución-de-problemas)
15. [Distribución del trabajo](#15-distribución-del-trabajo)

---

## 1. Entregables

| Entregable | Ubicación |
|---|---|
| Código fuente | `src/`, `pom.xml` |
| **Diagrama de arquitectura** | `docs/arquitectura/arquitectura.png` (también `.svg` y fuente Mermaid `.mmd`) |
| Diagrama del flujo SAGA y compensaciones | `docs/arquitectura/saga-flujo.png` (fuente `.mmd`) |
| Despliegue reproducible | `Dockerfile`, `docker-compose.yml`, `docker/post-boot-commands.asadmin` |
| Pruebas unitarias | `src/test/` (50 pruebas, JUnit 5 y Mockito) |
| Pruebas de la API | `docs/postman/taller-jee.postman_collection.json` (32 peticiones, 46 verificaciones) |

---

## 2. Inicio rápido

Con Docker Desktop abierto, desde la carpeta del proyecto:

```bash
docker compose up -d --build
```

El primer build tarda varios minutos porque descarga las imágenes y las dependencias de Maven. Cuando termine, la aplicación responde en:

```text
http://localhost:8081/taller/api/health   →   {"status":"UP"}
```

Para probar todos los casos, importa `docs/postman/taller-jee.postman_collection.json` en Postman y ejecútala con el **Collection Runner**, o desde la terminal:

```bash
npx newman run docs/postman/taller-jee.postman_collection.json
```

---

## 3. Requisitos

| Para | Necesitas |
|---|---|
| Ejecutar la aplicación | Docker Desktop (incluye Docker Compose) |
| Compilar y correr las pruebas unitarias fuera de Docker (opcional) | JDK 17 o superior y Maven 3.9 |
| Correr la colección de Postman por terminal (opcional) | Node.js 18 o superior (`npx newman`) |
| Regenerar los diagramas (opcional) | Node.js (`npx @mermaid-js/mermaid-cli`) |

No hace falta instalar Payara ni PostgreSQL: los dos corren en contenedores.

---

## 4. Ejecución con Docker

### Servicios

`docker-compose.yml` levanta dos contenedores:

| Contenedor | Imagen | Puerto en el equipo | Uso |
|---|---|---|---|
| `app` | Payara Server 6 Full (JDK 17) con `taller.war` | `8081` → API REST · `4848` → consola de Payara | Aplicación |
| `db` | PostgreSQL 16 con volumen persistente `pgdata` | (solo red interna) | Base de datos |

El `Dockerfile` compila el proyecto con Maven en una primera etapa y en la segunda copia el WAR a Payara junto con el driver JDBC de PostgreSQL. Al arrancar, Payara ejecuta `docker/post-boot-commands.asadmin`, que crea el pool de conexiones `TallerPool` y el recurso JTA `jdbc/taller`. El contenedor `app` espera a que la base de datos esté lista (healthcheck) antes de iniciar.

### Comandos

```bash
docker compose up -d --build   # construir y levantar
docker compose ps              # ver el estado de los contenedores
docker compose logs -f app     # ver el log de Payara
docker compose down            # detener (conserva los datos)
docker compose down -v         # detener y BORRAR los datos de PostgreSQL
```

### Direcciones

| Servicio | Dirección |
|---|---|
| API REST | `http://localhost:8081/taller/api` |
| Health check | `http://localhost:8081/taller/api/health` |
| Consola de administración de Payara | `https://localhost:4848` (usuario `admin`, contraseña `admin`, valores de la imagen oficial) |

> Las tablas se crean automáticamente al desplegar, pero no se modifican si ya existen. Si el modelo de datos cambia, ejecuta `docker compose down -v` antes de volver a levantar.

---

## 5. Cómo probar el sistema

### 5.1 Colección de Postman (recomendado)

`docs/postman/taller-jee.postman_collection.json` se importa en Postman o en Bruno. Está organizada en carpetas que se ejecutan en orden y cada petición tiene verificaciones automáticas:

| Carpeta | Qué prueba |
|---|---|
| 0. Salud | La aplicación está arriba |
| 1. Productos | Registrar, consultar, actualizar precio, existencias y disponibilidad |
| 2. Inventario | Inventario disponible, reservado y vendido |
| 3. SAGA de pedidos | Los cuatro escenarios: éxito, inventario insuficiente, pago rechazado y fallo al confirmar, con la verificación de cada compensación |
| 4. Pagos y SAGA | Consultas de pagos y ejecuciones de la SAGA |
| 5. Manejo de errores | 400, 404 y 405 con el formato JSON uniforme |

La variable `baseUrl` vale `http://localhost:8081/taller/api`. Los ids del producto y de cada pedido se guardan solos entre peticiones.

### 5.2 Recorrido manual de los escenarios

**1. Registrar un producto** (`POST /products`):

```json
{ "name": "Laptop", "description": "14 pulgadas", "price": 3000000, "stock": 5 }
```

**2. Crear pedidos** (`POST /orders`). Los campos `simulate...` solo existen para demostrar las compensaciones:

| Escenario | Cuerpo | Resultado |
|---|---|---|
| Éxito | `{"customerId":"C1","items":[{"productId":1,"quantity":2}]}` | `201`, pedido `CONFIRMADO` |
| Inventario insuficiente | `{"customerId":"C2","items":[{"productId":1,"quantity":50}]}` | `409 INSUFFICIENT_STOCK`, pedido `CANCELADO` |
| Pago rechazado | `{"customerId":"C3","items":[{"productId":1,"quantity":1}],"simulatePaymentFailure":true}` | `402 PAYMENT_REJECTED`, inventario liberado, pedido `CANCELADO` |
| Fallo al confirmar | `{"customerId":"C4","items":[{"productId":1,"quantity":1}],"simulateConfirmationFailure":true}` | `500 CONFIRMATION_FAILED`, pago reversado, inventario liberado, pedido `CANCELADO` |

**3. Revisar el resultado de cada pedido:**

- `GET /orders` y `GET /orders/{id}`: estado del pedido.
- `GET /sagas/order/{id}`: estado de la SAGA y **bitácora de pasos**.
- `GET /inventory/{productId}`: unidades disponibles, reservadas y vendidas.
- `GET /inventory/reservations/{id}` y `GET /payments/{id}`: estado de la reserva y del pago.

Tras los cuatro escenarios, el inventario de la laptop queda en `availableStock: 3`, `soldStock: 2` y `reservedStock: 0`. Solo se vendió el pedido exitoso, y las tres compensaciones devolvieron el stock.

### 5.3 Pruebas unitarias

```bash
mvn clean test
```

Cubren las reglas de los productos, la reserva atómica, la confirmación y la liberación idempotente del inventario, los pagos y su idempotencia, la máquina de estados del pedido, la bitácora de la SAGA y todos los caminos del orquestador, incluidas las compensaciones y la falla de una compensación. Total: 50 pruebas.

---

## 6. Arquitectura

### Diagrama de arquitectura

![Diagrama de arquitectura](docs/arquitectura/arquitectura.png)

### Estilo: monolito modular

La aplicación se despliega como **un único WAR** en Payara, dividido internamente en módulos por dominio con límites claros:

| Capa / módulo | Responsabilidad | Componentes principales |
|---|---|---|
| **API REST** (JAX-RS) | Única interfaz externa; traduce HTTP/JSON a comandos y consultas | `ProductResource`, `InventoryResource`, `OrderResource`, `PaymentResource`, `SagaResource`, `ExceptionMappers` |
| **Orquestador SAGA** | Coordina la compra y las compensaciones | `OrderSagaOrchestrator`, `SagaExecution`, `SagaQueryService` |
| **Productos** | CRUD de productos y consulta de precios | `ProductCommandService`, `ProductQueryServiceImpl` |
| **Inventario** | Reservar, confirmar y liberar existencias | `InventoryServiceImpl`, `InventoryQueryService` |
| **Pedidos** | Pedido, ítems y transiciones de estado | `OrderCommandService`, `OrderQueryService` |
| **Pagos** | Aprobar, rechazar y reversar pagos | `PaymentServiceImpl`, `PaymentQueryService` |
| **Shared** | Contratos entre módulos y errores comunes | interfaces de `shared.contract`, `ErrorCode`, `DomainException` |

Los módulos se comunican **dentro del proceso mediante interfaces CDI** (`ProductQueryService`, `InventoryService`, `PaymentService`). No hay llamadas HTTP entre componentes de la misma aplicación. El código usa solo APIs estándar de Jakarta EE (`jakarta.persistence`, `jakarta.transaction`, `jakarta.enterprise`, `jakarta.ws.rs`, `jakarta.validation`), así que es portable a otro servidor compatible cambiando únicamente la configuración del datasource.

### Estructura del código

```text
src/main/java/co/edu/javeriana/pedidos
├── api            resource (endpoints) · request · response · exception (mappers)
├── saga           orchestrator · model (SagaExecution, SagaStep) · query · dto
├── productos      command · query · model · dto
├── inventario     command · query · model · dto
├── pedidos        command · query · model · dto · exception
├── pagos          command · query · model · dto
└── shared         contract (interfaces y estados) · exception (ErrorCode y excepciones)
```

---

## 7. Patrones y conceptos aplicados

### 7.1 JPA: mapeo objeto-relacional

Las entidades se mapean con anotaciones estándar de JPA en la unidad de persistencia `tallerPU`. Las listas de ítems se modelan como `@ElementCollection` de objetos `@Embeddable`. `Product`, `CustomerOrder` y `SagaExecution` usan `@Version` (bloqueo optimista), y las operaciones críticas usan bloqueo pesimista (`LockModeType.PESSIMISTIC_WRITE`).

### 7.2 JTA: gestión de transacciones

Todas las transacciones las administra el contenedor mediante `@Transactional`, sobre el datasource JTA `jdbc/taller`.

- **Cada paso de la SAGA es una transacción local** (`REQUIRES_NEW`). El orquestador se declara `NOT_SUPPORTED`, de modo que la compra **no** es una transacción gigante. Si un paso falla, el contenedor revierte solo ese paso y el orquestador compensa los pasos ya confirmados.
- **Reserva de inventario atómica:** bloquea los productos (`SELECT ... FOR UPDATE`, siempre en orden ascendente de id para evitar interbloqueos), valida todo el pedido y solo entonces descuenta las existencias y registra la reserva. Si un producto no alcanza, no se modifica ninguno.
- **Pedido y SAGA alineados:** el pedido y su `SagaExecution` se crean y actualizan en la misma transacción.
- **Concurrencia verificada:** 8 pedidos simultáneos sobre un producto con 3 unidades dan exactamente 3 confirmados y 5 cancelados, y el stock nunca queda negativo.

### 7.3 SAGA por orquestación

![Flujo de la SAGA](docs/arquitectura/saga-flujo.png)

**Por qué orquestación y no coreografía:**

- El flujo es lineal y tiene un orden fijo (pedido → inventario → pago → confirmación), con una compensación clara para cada paso.
- Un componente central conoce el orden de los pasos, registra el avance y ejecuta las compensaciones en orden inverso. Con coreografía esa lógica quedaría repartida entre módulos que reaccionan a eventos, lo que dificulta seguir y depurar el flujo.
- El estado completo de cada compra queda en un solo lugar (`SagaExecution`), consultable en `GET /sagas/order/{orderId}`.
- **Costo asumido:** el orquestador conoce a todos los participantes y es un punto central de coordinación. En un monolito modular de cuatro módulos este acoplamiento es aceptable, y se limita a las interfaces de `shared.contract`.

**Pasos:**

1. **Crear pedido:** consulta los precios en Productos y registra el pedido `PENDIENTE` con su SAGA `IN_PROGRESS`. Los precios quedan guardados en cada ítem.
2. **Reservar inventario:** reserva `ACTIVA` y pedido `INVENTARIO_RESERVADO`.
3. **Procesar pago** por el total del pedido: pago `APROBADO` y pedido `PAGO_APROBADO`.
4. **Confirmar:** reserva `CONFIRMADA` (stock vendido), pedido `CONFIRMADO` y SAGA `COMPLETED`.

**Compensaciones:**

| Fallo | Compensación (en orden inverso) | HTTP |
|---|---|---|
| Inventario insuficiente o producto inactivo | Cancelar el pedido, sin procesar el pago | 409 |
| Pago rechazado | Liberar el inventario y cancelar el pedido | 402 |
| Fallo al confirmar | Reversar el pago, liberar el inventario y cancelar el pedido | 500 |

En los tres casos el pedido queda `CANCELADO` y la SAGA `COMPENSATED`. Las compensaciones son **idempotentes**: liberar o reversar dos veces no duplica el stock ni las reversiones. Si una compensación falla, se intentan las demás y la SAGA queda en `FAILED` con el motivo registrado.

**Bitácora de pasos:** cada ejecución guarda la secuencia completa con hora, por ejemplo para un fallo después del pago:

```text
PEDIDO_CREADO > INVENTARIO_RESERVADO > PAGO_APROBADO > COMPENSANDO > PAGO_REVERSADO > INVENTARIO_LIBERADO > PEDIDO_CANCELADO
```

### 7.4 CQRS

Cada módulo separa el modelo de escritura del de lectura:

- **Comandos** (paquetes `command`): servicios transaccionales que modifican entidades JPA y aplican sus reglas de negocio.
- **Consultas** (paquetes `query`): servicios que solo leen (`@Transactional(SUPPORTS)`) y devuelven DTO de lectura distintos de las entidades, en varios casos proyectados directamente con JPQL (`SELECT NEW ...`).
- En la API, los métodos `POST/PUT/DELETE` usan el servicio de comandos y los `GET` el de consultas.

El alcance del taller usa una misma base de datos para ambos lados (separación lógica, no física).

### 7.5 Manejo de errores

Las excepciones de negocio (`DomainException`) llevan un `ErrorCode` con su código HTTP, y los `ExceptionMappers` las traducen a un JSON uniforme (ver [sección 11](#11-manejo-de-errores)).

---

## 8. Requisitos del taller y su implementación

| Requisito del enunciado | Implementación |
|---|---|
| RF1. Gestión de productos: registrar, consultar, actualizar precio y disponibilidad, consultar inventario | `/products` (POST, GET, PUT `/price`, `/stock`, `/availability`) y `/inventory` |
| RF2. Creación de pedidos con cliente, productos y cantidades, iniciando en `PENDIENTE` | `POST /orders` → `OrderCommandService.create` |
| RF3. Procesamiento: crear → reservar → pagar → confirmar | `OrderSagaOrchestrator` con estados `PENDIENTE → INVENTARIO_RESERVADO → PAGO_APROBADO → CONFIRMADO` |
| Flujo de fallo: pago rechazado → liberar inventario → cancelar pedido | Compensación del orquestador, demostrable con `simulatePaymentFailure` |
| Ejecutar las compensaciones cuando una etapa falla | Reversar pago, liberar inventario y cancelar pedido, idempotentes y en orden inverso |
| JPA para la persistencia | Entidades `Product`, `InventoryReservation`, `CustomerOrder`, `Payment`, `SagaExecution` |
| JTA para transacciones entre recursos transaccionales | `@Transactional` administrado por Payara sobre `jdbc/taller`, una transacción local por paso |
| SAGA, justificando orquestación o coreografía | Orquestación, justificada en la [sección 7.3](#73-saga-por-orquestación) |
| CQRS | Servicios `command` y `query` separados en todos los módulos |
| Diagrama de arquitectura con patrones y componentes | `docs/arquitectura/arquitectura.png` |

---

## 9. Modelo de datos y estados

### Tablas

| Entidad | Tabla(s) | Contenido |
|---|---|---|
| `Product` | `products` | nombre, descripción, precio, existencias, activo |
| `InventoryReservation` | `inventory_reservations`, `reservation_items` | una reserva por pedido, con sus ítems y fechas |
| `CustomerOrder` | `customer_orders`, `order_items` | cliente, estado, total e ítems con el precio de compra |
| `Payment` | `payments` | un pago por pedido, con monto, estado y fechas |
| `SagaExecution` | `saga_executions`, `saga_steps` | estado de la SAGA, motivo de fallo y bitácora de pasos |

### Estados

| Elemento | Estados |
|---|---|
| Pedido | `PENDIENTE` → `INVENTARIO_RESERVADO` → `PAGO_APROBADO` → `CONFIRMADO` · `CANCELADO` desde cualquier estado intermedio |
| Reserva | `ACTIVA` (pedido en curso) → `CONFIRMADA` (stock vendido) · `LIBERADA` (stock devuelto) |
| Pago | `PENDIENTE` → `APROBADO` o `RECHAZADO` · `APROBADO` → `REVERSADO` |
| SAGA | `IN_PROGRESS` → `COMPLETED` · `IN_PROGRESS` → `COMPENSATING` → `COMPENSATED` o `FAILED` |

Las transiciones del pedido se validan en la entidad: no se puede saltar pasos ni salir de `CONFIRMADO` o `CANCELADO`.

---

## 10. Referencia de la API REST

URL base: `http://localhost:8081/taller/api`. Todas las peticiones y respuestas son JSON.

### Productos

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/products` | Registrar un producto: `{name, description?, price, stock}` |
| GET | `/products` | Consultar todos los productos |
| GET | `/products/{id}` | Consultar un producto |
| PUT | `/products/{id}/price` | Actualizar el precio: `{price}` |
| PUT | `/products/{id}/stock` | Actualizar las existencias: `{stock}` |
| PUT | `/products/{id}/availability` | Activar o desactivar: `{active}` |

### Inventario

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/inventory` | Inventario de todos los productos |
| GET | `/inventory/{productId}` | Inventario de un producto: `availableStock`, `reservedStock`, `soldStock` |
| GET | `/inventory/reservations/{orderId}` | Reserva de un pedido |
| POST | `/inventory/reservations` | Prueba aislada: reservar `{orderId, items}` |
| PUT | `/inventory/reservations/{orderId}/confirm` | Prueba aislada: confirmar una reserva |
| DELETE | `/inventory/reservations/{orderId}` | Prueba aislada: liberar una reserva |

### Pedidos

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/orders` | Crear y procesar un pedido (ejecuta la SAGA) |
| GET | `/orders` | Consultar todos los pedidos |
| GET | `/orders/{id}` | Consultar un pedido |

Cuerpo de `POST /orders`:

```json
{
  "customerId": "CLIENTE-001",
  "items": [
    { "productId": 1, "quantity": 2 }
  ],
  "simulatePaymentFailure": false,
  "simulateConfirmationFailure": false
}
```

Los dos campos `simulate...` son opcionales (por defecto `false`) y sirven solo para demostrar las compensaciones; no se guardan en el pedido.

### Pagos

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/payments` | Consultar todos los pagos |
| GET | `/payments/{orderId}` | Pago de un pedido |
| POST | `/payments` | Prueba aislada: procesar `{orderId, amount, simulateFailure}` |
| DELETE | `/payments/{orderId}` | Prueba aislada: reversar un pago |

### SAGA y salud

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/sagas` | Todas las ejecuciones de la SAGA |
| GET | `/sagas/order/{orderId}` | Estado y bitácora de la SAGA de un pedido |
| GET | `/health` | Estado de la aplicación |

Los endpoints marcados como "prueba aislada" permiten probar cada módulo por separado. En una compra real la SAGA invoca esas operaciones internamente mediante CDI.

---

## 11. Manejo de errores

Todas las respuestas de error tienen el mismo formato:

```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Inventario insuficiente para el producto 1 (solicitado 50, disponible 3)",
  "timestamp": "2026-10-11T03:20:30.069Z"
}
```

| HTTP | Situación | `code` |
|---|---|---|
| 400 | Datos inválidos o JSON mal formado | `VALIDATION_ERROR`, `MALFORMED_REQUEST` |
| 402 | Pago rechazado | `PAYMENT_REJECTED` |
| 404 | Producto, pedido, reserva, pago, SAGA o ruta inexistente | `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `RESERVATION_NOT_FOUND`, `PAYMENT_NOT_FOUND`, `SAGA_NOT_FOUND`, `RESOURCE_NOT_FOUND` |
| 405 | Método HTTP no permitido | `METHOD_NOT_ALLOWED` |
| 409 | Inventario insuficiente, producto inactivo o transición de estado inválida | `INSUFFICIENT_STOCK`, `INVALID_STATE` |
| 415 | El cuerpo no se envió como JSON | `UNSUPPORTED_MEDIA_TYPE` |
| 500 | Fallo simulado de confirmación, compensación fallida o error interno | `CONFIRMATION_FAILED`, `COMPENSATION_FAILED`, `INTERNAL_ERROR` |

`DomainExceptionMapper` traduce los errores de negocio, `ConstraintViolationExceptionMapper` los de Bean Validation, y `GlobalExceptionMapper` conserva el código de los errores propios de JAX-RS y responde 500 solo ante fallos inesperados, que además quedan en el log.

---

## 12. Configuración

| Archivo | Contenido |
|---|---|
| `pom.xml` | Java 17, Jakarta EE 10 (`jakarta.jakartaee-api`, scope `provided`), JUnit 5, Mockito, empaquetado WAR `taller.war` |
| `src/main/resources/META-INF/persistence.xml` | Unidad `tallerPU`, tipo JTA, datasource `jdbc/taller`, creación automática del esquema |
| `src/main/webapp/WEB-INF/beans.xml` | CDI con `bean-discovery-mode="all"` |
| `docker/post-boot-commands.asadmin` | Crea el pool `TallerPool` (PostgreSQL `db:5432/taller`, usuario y contraseña `taller`) y el recurso `jdbc/taller` |
| `Dockerfile` | Build Maven en dos etapas y despliegue en `payara/server-full:6.2024.6-jdk17` |
| `docker-compose.yml` | Servicios `app` y `db`, puertos y volumen |

Para compilar sin Docker: `mvn clean package` genera `target/taller.war`, desplegable en cualquier Payara 6 que tenga definido el recurso JDBC `jdbc/taller`.

---

## 13. Decisiones de diseño y alcance

| Decisión | Motivo | Costo o alternativa descartada |
|---|---|---|
| Monolito modular en un WAR | Un solo despliegue, límites claros por dominio y comunicación en proceso | Microservicios: más infraestructura sin un requisito que la justifique |
| Comunicación por interfaces CDI | Desacopla los módulos sin el costo de llamadas HTTP internas | Los módulos dependen de los contratos de `shared.contract` |
| SAGA por orquestación | Flujo lineal, fácil de seguir, probar y depurar; estado centralizado | Coreografía: menos acoplamiento central, pero el flujo queda disperso |
| Una transacción JTA por paso | Evita bloquear recursos durante toda la compra | Requiere compensaciones explícitas en lugar de un rollback global |
| Bloqueo pesimista en inventario | Impide la sobreventa con pedidos concurrentes | Serializa las reservas sobre el mismo producto |
| Compensaciones idempotentes | Los reintentos y las compensaciones repetidas son seguros | Lógica adicional de verificación de estado |
| CQRS lógico con una base de datos | Separa responsabilidades sin duplicar infraestructura | No hay escalado independiente de lecturas |
| Payara 6 y PostgreSQL en Docker | Despliegue reproducible con un comando | Requiere Docker Desktop |

**Alcance de JTA:** las transacciones las administra el contenedor sobre un único recurso transaccional (PostgreSQL). No se usa una transacción distribuida XA. La compra completa se coordina con la SAGA precisamente para evitar una transacción distribuida que bloquee recursos durante todo el proceso.

**Simulación de fallos:** el pago es simulado (no hay pasarela externa). Los campos `simulatePaymentFailure` y `simulateConfirmationFailure` existen solo para demostrar las compensaciones.

---

## 14. Solución de problemas

| Problema | Solución |
|---|---|
| `port is already allocated` en 8081 o 4848 | Otro programa usa el puerto: cambia el lado izquierdo de `ports` en `docker-compose.yml` (por ejemplo `"8082:8080"`) |
| La API no responde justo después de `up` | Payara tarda entre 30 y 60 segundos en desplegar: revisa `docker compose logs -f app` hasta ver `taller was successfully deployed` |
| Errores de columnas o tablas faltantes | El esquema cambió y la base conserva el anterior: `docker compose down -v` y `docker compose up -d --build` |
| El primer build es muy lento | Es normal: descarga Payara, PostgreSQL y las dependencias de Maven. Los siguientes builds usan caché |
| Docker no responde | Reinicia Docker Desktop |

---

## 15. Distribución del trabajo

**Rol 1**

- Configuración base: Maven, JPA, JTA, datasource, Docker y Docker Compose.
- Módulo de productos (CRUD con CQRS).
- Módulo de inventario: reserva atómica, confirmación y liberación idempotente, con CQRS.
- Pruebas unitarias de productos e inventario.
- Diagramas de arquitectura y de flujo SAGA.
- Colección de pruebas de Postman y documentación.

**Rol 2**

- Módulos de pedidos y pagos (con CQRS).
- Orquestador SAGA y operaciones compensatorias.
- Manejo global de errores.
- API REST de pedidos, pagos y seguimiento de la SAGA.
- Pruebas de pagos y de la SAGA.
