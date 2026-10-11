# Sistema de Gestión de Pedidos con Jakarta EE

Aplicación empresarial para gestionar productos, inventario, pedidos y pagos. Implementa **JPA**, **JTA**, **CQRS** y el patrón **SAGA por orquestación**, incluyendo operaciones compensatorias ante fallos.

## Tecnologías

- Java 17
- Jakarta EE 10
- Payara Server 6
- JPA
- JTA
- CDI
- JAX-RS
- PostgreSQL 16
- Maven
- Docker y Docker Compose
- JUnit 5 y Mockito

## Arquitectura

La aplicación utiliza una arquitectura de **monolito modular**, desplegada como un único archivo WAR en Payara Server.

Los módulos se comunican internamente mediante interfaces y servicios CDI. REST se utiliza únicamente como API externa.

```text
co.edu.javeriana.pedidos
├── productos
│   ├── command
│   ├── query
│   ├── model
│   └── dto
├── inventario
│   ├── command
│   ├── query
│   ├── model
│   └── dto
├── pedidos
│   ├── command
│   ├── query
│   ├── model
│   └── dto
├── pagos
│   ├── command
│   ├── query
│   ├── model
│   └── dto
├── saga
│   ├── orchestrator
│   ├── model
│   ├── query
│   └── dto
├── shared
│   ├── contract
│   └── exception
└── api
    ├── resource
    ├── request
    ├── response
    └── exception
```

### Diagrama de arquitectura

![Diagrama de arquitectura](docs/arquitectura/arquitectura.png)

## CQRS

Las operaciones de escritura y lectura se encuentran separadas:

- Los paquetes `command` contienen los servicios que modifican información.
- Los paquetes `query` contienen servicios dedicados exclusivamente a consultas.
- Los DTO de lectura son diferentes de las entidades JPA utilizadas para escritura y persistencia.

Esta separación se aplica en los módulos de productos, inventario, pedidos y pagos.

## SAGA por orquestación

La compra se coordina desde `OrderSagaOrchestrator`.

Se eligió la **orquestación** y no la coreografía porque:

- El flujo es lineal y tiene un orden fijo (pedido → inventario → pago → confirmación), con una compensación clara para cada paso.
- Un componente central conoce el orden de los pasos, registra el avance de la SAGA y ejecuta las compensaciones en orden inverso. Con coreografía esa lógica quedaría repartida entre módulos que reaccionan a eventos, lo que dificulta seguir y depurar el flujo.
- El estado completo de cada compra queda en un solo lugar (`SagaExecution`) y se consulta en `GET /sagas/order/{orderId}`.
- Costo asumido: el orquestador conoce a todos los participantes y es un punto central de coordinación. En un monolito modular con cuatro módulos este acoplamiento es aceptable y se limita a las interfaces de `shared.contract`.

El flujo exitoso es:

1. Consultar los productos, guardar sus precios y registrar el pedido en estado `PENDIENTE` junto con su `SagaExecution` (misma transacción).
2. Reservar el inventario y cambiar el pedido a `INVENTARIO_RESERVADO`.
3. Procesar el pago por el total del pedido y cambiarlo a `PAGO_APROBADO`.
4. Confirmar la reserva (`CONFIRMADA`: el stock queda vendido) y el pedido (`CONFIRMADO`). La SAGA termina en `COMPLETED`.

Cada paso queda registrado en la **bitácora de la SAGA** con su hora. Por ejemplo, un fallo después del pago deja:

```text
PEDIDO_CREADO > INVENTARIO_RESERVADO > PAGO_APROBADO > COMPENSANDO > PAGO_REVERSADO > INVENTARIO_LIBERADO > PEDIDO_CANCELADO
```

### Flujo y compensaciones

![Flujo de la SAGA](docs/arquitectura/saga-flujo.png)

Las compensaciones implementadas son:

| Fallo | Compensación | Respuesta |
|---|---|---|
| Inventario insuficiente o producto inactivo | Cancelar el pedido sin procesar el pago | 409 |
| Pago rechazado | Liberar el inventario y cancelar el pedido | 402 |
| Fallo al confirmar el pedido | Reversar el pago, liberar el inventario y cancelar el pedido | 500 |

En todos los casos el pedido queda registrado en estado `CANCELADO` y la SAGA en `COMPENSATED`.

Las operaciones de liberación de inventario y reversión de pagos son idempotentes: ejecutarlas más de una vez no duplica el inventario ni genera múltiples reversiones. Si una compensación falla, se intentan las demás y la SAGA queda en `FAILED` con el motivo registrado.

## Transacciones JTA

La compra completa no se ejecuta como una única transacción, porque eso mantendría bloqueados los recursos durante todo el proceso.

El orquestador se declara `@Transactional(NOT_SUPPORTED)` y cada paso que invoca utiliza su propia transacción JTA administrada por Payara (`@Transactional(REQUIRES_NEW)`):

- **Reserva de inventario:** bloquea los productos (`SELECT ... FOR UPDATE`, siempre en orden de id para evitar interbloqueos), valida todo el pedido y solo entonces descuenta las existencias y registra la reserva. Si un producto no alcanza, no se modifica ninguno.
- **Pedido y SAGA:** el pedido y su `SagaExecution` se crean y se actualizan en la misma transacción, de modo que nunca quedan desalineados.
- **Pago:** registra su resultado de forma transaccional e idempotente.
- Si un paso falla, el contenedor revierte su transacción y el orquestador ejecuta las compensaciones de los pasos ya confirmados.

Se verificó con 8 pedidos simultáneos sobre un producto con 3 unidades: 3 quedan confirmados, 5 cancelados y el inventario nunca queda negativo.

## Estados

### Pedido

```text
PENDIENTE
INVENTARIO_RESERVADO
PAGO_APROBADO
CONFIRMADO
CANCELADO
```

### Pago

```text
PENDIENTE
APROBADO
RECHAZADO
REVERSADO
```

### Reserva

```text
ACTIVA       (pedido en curso)
CONFIRMADA   (pedido confirmado: stock vendido)
LIBERADA     (compensación: stock devuelto)
```

### SAGA

```text
IN_PROGRESS
COMPLETED
COMPENSATING
COMPENSATED
FAILED
```

## Ejecución con Docker

### Requisitos

- Docker Desktop
- Docker Compose

### Iniciar la aplicación

```bash
docker compose up -d --build
```

El primer build puede tardar varios minutos mientras se descargan las imágenes y dependencias.

Comprobar los contenedores:

```bash
docker compose ps
```

Los servicios disponibles son:

| Servicio | Dirección |
|---|---|
| API REST | `http://localhost:8081/taller/api` |
| Consola de Payara | `http://localhost:4848` |
| Health check | `http://localhost:8081/taller/api/health` |

En PowerShell, el estado de la aplicación se puede comprobar con:

```powershell
Invoke-RestMethod http://localhost:8081/taller/api/health
```

Respuesta esperada:

```json
{
  "status": "UP"
}
```

### Detener la aplicación

```bash
docker compose down
```

Para eliminar también los datos almacenados en PostgreSQL:

```bash
docker compose down -v
```

Las tablas se crean automáticamente al desplegar (`jakarta.persistence.schema-generation.database.action=create`), pero no se modifican si ya existen. Si cambia el modelo de datos, se debe ejecutar `docker compose down -v` antes de volver a levantar la aplicación.

## Ejecución de pruebas

Para compilar el proyecto y ejecutar las pruebas:

```bash
mvn clean test
```

Las pruebas unitarias (JUnit 5 y Mockito) verifican las reglas de productos, la reserva atómica e idempotente del inventario, los pagos, la máquina de estados del pedido, la bitácora de la SAGA y todos los resultados del orquestador, incluidas las compensaciones.

### Pruebas de la API con Postman

La colección `docs/postman/taller-jee.postman_collection.json` recorre todos los casos: CRUD de productos, inventario, los cuatro escenarios de la SAGA (éxito, inventario insuficiente, pago rechazado y fallo al confirmar), consultas y manejo de errores. Cada petición incluye verificaciones automáticas.

Se puede importar en Postman o Bruno, o ejecutar desde la terminal con la aplicación levantada:

```bash
npx newman run docs/postman/taller-jee.postman_collection.json
```

## API REST

URL base:

```text
http://localhost:8081/taller/api
```

### Productos

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/products` | Registrar un producto |
| GET | `/products` | Consultar todos los productos |
| GET | `/products/{id}` | Consultar un producto |
| PUT | `/products/{id}/price` | Actualizar el precio |
| PUT | `/products/{id}/stock` | Actualizar el inventario |
| PUT | `/products/{id}/availability` | Activar o desactivar un producto |

Ejemplo de creación:

```json
{
  "name": "Teclado mecánico",
  "price": 150000,
  "stock": 10
}
```

### Inventario

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/inventory` | Consultar el inventario |
| GET | `/inventory/{productId}` | Consultar el inventario de un producto |
| GET | `/inventory/reservations/{orderId}` | Consultar la reserva de un pedido |
| POST | `/inventory/reservations` | Probar una reserva directamente |
| PUT | `/inventory/reservations/{orderId}/confirm` | Probar la confirmación de una reserva |
| DELETE | `/inventory/reservations/{orderId}` | Liberar una reserva |

El inventario de cada producto informa `availableStock` (unidades libres), `reservedStock` (apartadas por pedidos en curso) y `soldStock` (vendidas en pedidos confirmados). Los endpoints de reserva sirven para probar el módulo aislado; en una compra, la SAGA invoca estas operaciones internamente.

### Pedidos

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/orders` | Crear y procesar un pedido |
| GET | `/orders` | Consultar todos los pedidos |
| GET | `/orders/{id}` | Consultar un pedido |

Ejemplo de pedido exitoso:

```json
{
  "customerId": "CLIENTE-001",
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "simulatePaymentFailure": false,
  "simulateConfirmationFailure": false
}
```

Para simular el rechazo del pago:

```json
{
  "customerId": "CLIENTE-PAGO-RECHAZADO",
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ],
  "simulatePaymentFailure": true,
  "simulateConfirmationFailure": false
}
```

Para simular un fallo posterior a la aprobación del pago:

```json
{
  "customerId": "CLIENTE-FALLO-CONFIRMACION",
  "items": [
    {
      "productId": 1,
      "quantity": 1
    }
  ],
  "simulatePaymentFailure": false,
  "simulateConfirmationFailure": true
}
```

Los campos de simulación son exclusivamente demostrativos y permiten comprobar las compensaciones de la SAGA.

### Pagos

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/payments` | Consultar los pagos |
| GET | `/payments/{orderId}` | Consultar el pago de un pedido |
| POST | `/payments` | Probar el procesamiento de un pago |
| DELETE | `/payments/{orderId}` | Reversar el pago de un pedido |

### SAGA

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/sagas` | Consultar todas las ejecuciones de la SAGA |
| GET | `/sagas/order/{orderId}` | Consultar el estado y la bitácora de pasos de la SAGA de un pedido |

## Manejo de errores

Los errores se entregan con una estructura JSON uniforme:

```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Inventario insuficiente para el producto solicitado",
  "timestamp": "2026-10-08T17:28:38Z"
}
```

Algunos códigos HTTP utilizados son:

| Código | Situación | `code` |
|---|---|---|
| 400 | Datos inválidos o JSON mal formado | `VALIDATION_ERROR`, `MALFORMED_REQUEST` |
| 402 | Pago rechazado | `PAYMENT_REJECTED` |
| 404 | Producto, pedido, reserva, pago, SAGA o ruta inexistente | `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `RESOURCE_NOT_FOUND`, ... |
| 405 | Método HTTP no permitido | `METHOD_NOT_ALLOWED` |
| 409 | Inventario insuficiente o estado inválido | `INSUFFICIENT_STOCK`, `INVALID_STATE` |
| 415 | El cuerpo no se envió como JSON | `UNSUPPORTED_MEDIA_TYPE` |
| 500 | Fallo simulado de confirmación, compensación fallida o error interno | `CONFIRMATION_FAILED`, `COMPENSATION_FAILED`, `INTERNAL_ERROR` |

Los errores de negocio los traduce `DomainExceptionMapper`, los de Bean Validation `ConstraintViolationExceptionMapper`, y `GlobalExceptionMapper` conserva el código de los errores propios de JAX-RS y responde 500 solo ante fallos inesperados.

## Persistencia

La aplicación utiliza PostgreSQL 16 mediante un datasource administrado por Payara:

```text
JNDI: jdbc/taller
Unidad de persistencia: tallerPU
```

Las entidades se administran mediante JPA y las transacciones mediante JTA.

## Distribución del trabajo

### Rol 1

- Configuración base del proyecto: Maven, JPA, JTA y datasource.
- Docker Compose, Payara y PostgreSQL.
- Módulo de productos (CRUD con CQRS).
- Módulo de inventario: reserva atómica, confirmación y liberación idempotente, con CQRS.
- Pruebas unitarias de productos e inventario.
- Diagrama de arquitectura.
- Colección de pruebas de Postman.

### Rol 2

- Manejo global de errores.
- Módulo de pagos.
- Módulo de pedidos.
- Orquestador SAGA.
- Operaciones compensatorias.
- API REST de pedidos, pagos y seguimiento de la SAGA.
- Pruebas de pagos y SAGA.
- Documentación del proyecto.

## Decisiones principales

- Monolito modular empaquetado como WAR.
- Payara 6 como servidor Jakarta EE.
- Comunicación interna mediante CDI.
- API externa mediante JAX-RS.
- CQRS para separar comandos y consultas.
- SAGA por orquestación.
- Una transacción JTA por cada paso.
- Compensaciones idempotentes y bitácora de pasos de la SAGA.
- Bloqueo pesimista en inventario para evitar sobreventa con pedidos concurrentes.
- PostgreSQL como base de datos de ejecución.
- Docker Compose para un despliegue reproducible.