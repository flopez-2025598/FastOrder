# FastOrder – API REST de Pedidos y Delivery

Backend de gestión de comercios, productos, pedidos y entregas desarrollado como un monolito modular.

## Tecnologías

- Java 21.
- Spring Boot 3.5.6.
- Spring Security con autenticación JWT sin sesiones.
- Spring Data JPA y Hibernate.
- PostgreSQL.
- JJWT 0.12.6.
- Lombok.
- Maven Wrapper.
- JUnit y H2 en memoria para pruebas automatizadas.

## Requisitos

- JDK 21.
- PostgreSQL instalado y ejecutándose, o Docker Compose para levantar PostgreSQL.
- Git Bash en Windows para ejecutar los scripts.
- curl.
- jq para ejecutar el script del profesor.

## Cómo ejecutar

Ejecutar los comandos desde la raíz del proyecto, donde se encuentra `pom.xml`.

### 1. Iniciar PostgreSQL

Si PostgreSQL está instalado en la computadora, iniciar su servicio.

Como alternativa, ejecutar PostgreSQL mediante Docker:

```bash
docker compose up -d
```

Docker Compose utiliza:

- Base de datos: `fastorder_fares_db`.
- Usuario: `postgres`.
- Contraseña: `admin`.
- Puerto: `5432`.

Usar una de estas opciones. Si PostgreSQL local ya ocupa el puerto 5432, detenerlo antes de levantar el contenedor con ese mismo puerto.

### 2. Iniciar la aplicación

Desde IntelliJ IDEA, ejecutar `FastorderApplication`.

Desde Git Bash en Windows:

```bash
./mvnw.cmd spring-boot:run
```

Desde Linux o macOS:

```bash
./mvnw spring-boot:run
```

La API se ejecuta, por defecto, en:

```text
http://localhost:8080
```

Durante el arranque:

1. `PostgresDatabaseCreator` verifica o crea `fastorder_fares_db` antes de iniciar JPA.
2. Hibernate crea o actualiza las tablas mediante `ddl-auto=update`.
3. `data.sql` carga los datos iniciales después de la creación de las tablas.

La creación automática requiere que el usuario de PostgreSQL tenga permiso para crear bases de datos. Puede desactivarse con `fastorder.db.auto-create=false` si la base ya está preparada.

## Configuración

La configuración principal está en:

```text
src/main/resources/application.properties
```

La conexión predeterminada es:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/fastorder_fares_db
```

Variables de entorno admitidas mediante la configuración actual:

| Variable | Uso | Valor predeterminado |
|---|---|---|
| `DB_USER` | Usuario de PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña de PostgreSQL | `admin` |
| `JWT_SECRET` | Clave para firmar y validar JWT | Clave de desarrollo definida en el archivo |
| `JWT_EXPIRATION` | Duración del token en milisegundos | `86400000` |
| `PORT` | Puerto HTTP de la aplicación | `8080` |

Para cambiar la URL de conexión, editar `spring.datasource.url` o utilizar la variable estándar de Spring Boot `SPRING_DATASOURCE_URL`.

Las credenciales incluidas son para desarrollo y pruebas. En un despliegue real deben configurarse credenciales propias y una clave JWT privada.

## Usuarios iniciales

`data.sql` precarga un usuario por rol. Las contraseñas se almacenan con BCrypt.

| Rol | Email | Contraseña de prueba |
|---|---|---|
| ADMIN | admin@fastorder.com | Admin123* |
| REPARTIDOR | repartidor@fastorder.com | Repartidor123* |
| CLIENTE | cliente.demo@fastorder.com | Cliente123* |

`cliente@fastorder.com` no se precarga: el script del profesor lo registra con `Cliente123*`.

Las inserciones comprueban si los datos ya existen para evitar duplicarlos en arranques sucesivos. El script también sincroniza las contraseñas y los roles de las cuentas de prueba indicadas.

Se precargan tres comercios:

- Burger King: RESTAURANTE.
- Super Express: SUPERMERCADO.
- Farmacia Salud: FARMACIA.

El catálogo inicial contiene cinco productos con precio y stock.

## Autenticación y roles

Todas las rutas de negocio requieren:

```http
Authorization: Bearer <token>
```

Los endpoints de registro y login son públicos.

- ADMIN: crea comercios y productos, consulta pedidos de la plataforma y gestiona estados y cancelaciones.
- REPARTIDOR: consulta pedidos disponibles o asignados y avanza sus estados.
- CLIENTE: consulta el catálogo, crea pedidos y consulta o cancela sus propios pedidos.

El registro público siempre asigna el rol CLIENTE, aunque la solicitud envíe otro rol.

## Endpoints

| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Público | Registrar un usuario con rol CLIENTE |
| POST | `/api/v1/auth/login` | Público | Autenticar y obtener un JWT |
| GET | `/api/v1/comercios` | Autenticado | Listar comercios abiertos |
| POST | `/api/v1/comercios` | ADMIN | Crear un comercio |
| GET | `/api/v1/comercios/{id}/productos` | Autenticado | Consultar el catálogo de un comercio |
| POST | `/api/v1/comercios/{id}/productos` | ADMIN | Agregar un producto |
| POST | `/api/v1/pedidos` | CLIENTE | Crear un pedido |
| GET | `/api/v1/pedidos/mis-pedidos` | CLIENTE | Consultar el historial propio |
| GET | `/api/v1/pedidos/disponibles` | REPARTIDOR, ADMIN | Consultar pedidos activos según el rol y la asignación |
| PATCH | `/api/v1/pedidos/{id}/estado` | REPARTIDOR, ADMIN | Avanzar al siguiente estado |
| PATCH | `/api/v1/pedidos/{id}/cancelar` | CLIENTE, ADMIN | Cancelar un pedido pendiente y restaurar stock |
| GET | `/api/v1/pedidos/{id}` | Autenticado | Consultar un pedido con validación de acceso |
| GET | `/api/v1/pedidos` | ADMIN | Consultar pedidos para auditoría |

Filtros opcionales:

```text
GET /api/v1/comercios?categoria=FARMACIA
GET /api/v1/pedidos?estado=ENTREGADO
```

### Crear un pedido

```http
POST /api/v1/pedidos
Content-Type: application/json
Authorization: Bearer <token_cliente>
```

```json
{
  "items": [
    {
      "productoId": 1,
      "cantidad": 2
    },
    {
      "productoId": 2,
      "cantidad": 1
    }
  ]
}
```

Los IDs deben corresponder a productos existentes.

### Actualizar un estado

```http
PATCH /api/v1/pedidos/5/estado
Content-Type: application/json
Authorization: Bearer <token_repartidor_o_admin>
```

```json
{
  "estado": "EN_PREPARACION"
}
```

## Reglas de negocio

### Cálculo de totales

El servidor calcula:

```text
subtotalProductos = suma del precio del catálogo × cantidad
montoTotal = subtotalProductos + costoEnvio
```

El costo de envío predeterminado es Q20.00 y se configura mediante `fastorder.costo-envio`.

Los precios, subtotales o totales enviados por el cliente no se utilizan para calcular el pedido. Cada detalle conserva el precio unitario aplicado al momento de la compra.

### Inventario y rollback

La creación de pedidos se realiza dentro de una transacción.

Antes de validar y descontar inventario, se bloquean los productos mediante `PESSIMISTIC_WRITE`, equivalente al bloqueo `SELECT ... FOR UPDATE` en PostgreSQL.

Los productos se bloquean en orden de ID para reducir el riesgo de deadlocks.

Se comprueba que:

- El producto exista.
- Esté disponible.
- Su comercio esté abierto.
- Tenga stock suficiente.

Si falla cualquier producto, se revierte la transacción completa: no se guarda el pedido ni quedan descuentos parciales de stock.

### Flujo de estados

```text
PENDIENTE → EN_PREPARACION → EN_CAMINO → ENTREGADO
```

Solo se permite avanzar un paso. No se puede saltar, retroceder ni modificar un pedido en estado final.

El primer repartidor que avanza un pedido sin asignación queda asignado a él. Otro repartidor no puede modificar ese pedido.

Los cambios de estado y las cancelaciones bloquean la fila del pedido para coordinar operaciones simultáneas.

### Cancelación

Un pedido solo puede cancelarse si está PENDIENTE.

- El cliente puede cancelar sus propios pedidos.
- El administrador puede cancelar pedidos pendientes de cualquier cliente.
- La cancelación restaura el inventario.
- Una segunda cancelación se rechaza y no devuelve stock adicional.

## Respuestas de error

Los errores utilizan una estructura JSON común basada en `ApiError`, el manejador global de excepciones y los manejadores de seguridad.

```json
{
  "timestamp": "2026-10-07T14:51:34",
  "status": 409,
  "error": "Conflict",
  "message": "Stock insuficiente para 'Whopper' (id 1): disponible 2, solicitado 5",
  "path": "/api/v1/pedidos",
  "detalles": {
    "productoId": "1",
    "stockDisponible": "2",
    "cantidadSolicitada": "5"
  }
}
```

El campo `detalles` se incluye cuando corresponde.

| Situación | HTTP |
|---|---|
| Datos inválidos o JSON mal formado | 400 |
| Estado o transición no permitidos | 400 |
| Sin token, token inválido o credenciales incorrectas | 401 |
| Rol o usuario sin permiso | 403 |
| Recurso inexistente | 404 |
| Email duplicado | 409 |
| Stock insuficiente | 409 |
| Conflicto de bloqueo o integridad de datos | 409 |
| Error interno inesperado | 500 |

## Pruebas

### Pruebas JUnit

En Git Bash de Windows:

```bash
./mvnw.cmd test
```

En Linux o macOS:

```bash
./mvnw test
```

Estas pruebas utilizan H2 en memoria y no requieren PostgreSQL ni una instancia de la API previamente iniciada. Las pruebas de integración levantan un servidor en un puerto aleatorio.

Cubren:

- Arranque del contexto de Spring.
- Reglas del enum de estados.
- Autenticación y permisos.
- Registro con rol CLIENTE y rechazo de emails duplicados.
- Totales calculados en el servidor.
- Descuento de inventario y rollback.
- Validación de solicitudes.
- Flujo de estados y asignación de repartidor.
- Cancelación, devolución de stock y permisos del propietario.
- Historial por cliente.
- Veinte compras concurrentes sobre un stock inicial de cinco.

### Script del profesor

Con la API ejecutándose y PostgreSQL disponible:

```bash
bash test-api4.sh
```

Requiere curl y jq. Comprueba registro, autenticación, creación de comercio y producto, creación de pedido y permisos.

Para la carga de lectura utiliza:

- ApacheBench, si está disponible: 500 solicitudes con concurrencia de 50.
- Como alternativa: 100 solicitudes con hasta 10 en paralelo mediante curl y xargs.

### Script ampliado

Con la API ejecutándose y los usuarios iniciales disponibles:

```bash
bash test-fastorder.sh
```

Comprueba seguridad, validaciones, catálogo, pedidos, rollback, estados, cancelaciones y concurrencia.

Por defecto ejecuta:

- 30 compras en paralelo contra un stock de 10.
- 10 cancelaciones simultáneas sobre un pedido.
- 10 cambios de estado simultáneos sobre otro pedido.
- 200 consultas al catálogo con hasta 20 en paralelo.

La carga se puede configurar:

```bash
STRESS_STOCK=10 STRESS_ORDERS=50 LOAD_REQUESTS=500 LOAD_CONCURRENCY=20 bash test-fastorder.sh
```

Ambos scripts crean datos de prueba en la base de datos configurada.

## Resultados verificados

Resultados de la ejecución realizada el 7 de octubre de 2026:

| Prueba | Resultado |
|---|---|
| Maven / JUnit con H2 | 13 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS |
| Script del profesor, carga con curl | 100 respuestas HTTP 200 |
| Script ampliado con PostgreSQL | 91 comprobaciones correctas, 0 fallidas |
| 30 compras contra stock de 10 | 10 respuestas 201, 20 respuestas 409; stock final 0 |
| 10 cancelaciones simultáneas | Una exitosa; inventario restaurado una sola vez |
| 10 cambios de estado simultáneos | Un cambio exitoso |
| 200 consultas con concurrencia de 20 | 200 respuestas HTTP 200 |

En esa ejecución, las 30 compras tardaron aproximadamente 861 ms y las 200 consultas tardaron 6038 ms, equivalentes a unas 33 solicitudes por segundo para ese bloque.

Los resultados describen esa ejecución y su entorno; no representan la capacidad máxima de la API.

## Estructura del proyecto

El código está organizado por módulos dentro de `com.fareslopez.fastorder`:

| Módulo | Responsabilidad |
|---|---|
| `auth` | Usuarios, registro, login, JWT y configuración de seguridad |
| `comercio` | Comercios, productos y catálogo |
| `pedido` | Pedidos, detalles, inventario y estados |
| `common` | Errores compartidos y configuración de creación de la base |

Cada módulo separa entidades JPA, DTO, controladores, servicios y repositorios según corresponda.

Los DTO definen las solicitudes y respuestas de la API, mientras las entidades representan los datos persistidos.