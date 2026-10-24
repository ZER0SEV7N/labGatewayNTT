

# 🏦 Arquitectura de Microservicios con Quarkus & Kong API Gateway

Implementación local de una arquitectura de microservicios gobernada por un **API Gateway (Kong en modo DB-less declarativo)** que reproduce los patrones corporativos de **Azure API Management (APIM)**: diseño *Contract-First* con OpenAPI, aislamiento de red en contenedores, planes de suscripción (*Rate Limiting*), control de acceso basado en roles/grupos (*ACL*), caché de respuestas (*Proxy Cache*), validación de esquemas (*Bean Validation*) y comunicación interna entre servicios con manejo de resiliencia.

---

## 🏗️ 1. Arquitectura del Sistema

El ecosistema corre íntegramente sobre una red privada de contenedores llamada `lab-net`. Los microservicios de negocio (`clientes-api` y `cuentas-api`) **no exponen puertos al host**; todo el tráfico externo ingresa exclusivamente a través de **Kong API Gateway** en el puerto `8000`.

```
[ Cliente (Postman / cURL) ]
             |
             |  HTTP + Header: Ocp-Apim-Subscription-Key
             v
+----------------------- Red Podman: lab-net -----------------------+
|                                                                   |
|   +-----------------------------------------------------------+   |
|   |                Kong API Gateway (api-gateway)             |   |
|   |            Proxy: :8000     |     Admin API: :8001        |   |
|   |   Plugins: key-auth, acl, rate-limiting, proxy-cache,     |   |
|   |            correlation-id, response-transformer           |   |
|   +-----------------------------------------------------------+   |
|          |                              |                 |       |
|          | /contrato                    | /api/v1         | /api/v1/cuentas
|          | (Público)                    | (Básico/Prem)   | (Solo Premium)
|          v                              v                 v       |
|   +------------------------------------------+     +--------------+
|   |         clientes-api (Quarkus)           |     | cuentas-api  |
|   |       Puerto interno: 8080 (H2 DB)       |<----|  (Quarkus)   |
|   |       OpenAPI Contract-First + CRUD      |     | Puerto: 8080 |
|   +------------------------------------------+     +--------------+
|                         ^                                 |       |
|                         +--- Valida existencia cliente ---+       |
|                              GET http://clientes-api:8080         |
+-------------------------------------------------------------------+

```

---

## 🔄 2. Equivalencia con Azure API Management (APIM)

| Concepto en Azure APIM | Implementación en este Proyecto | Componente / Plugin |
| --- | --- | --- |
| **API Gateway** | Contenedor Kong DB-less escuchando en el puerto `8000`

| `kong:3.6` (`gateway/kong.yml`)

|
| **Contract-First** | Contrato estático servido antes de la codificación

| `META-INF/openapi.yaml` (`mp.openapi.scan.disable=true`)

|
| **Developer Portal** | Ruta pública de consulta de especificación OpenAPI

| Ruta `/contrato` $\rightarrow$ `/q/openapi`

|
| **Subscription Key** | Autenticación mediante cabecera HTTP estándar de APIM

| Plugin `key-auth` (`Ocp-Apim-Subscription-Key`)

|
| **Productos y Planes** | Segmentación de consumidores por nivel de suscripción

| Consumidores `app-movil` (Básico) y `app-web` (Premium)

|
| **Control de Acceso a APIs** | Restricción de rutas exclusivas por producto

| Plugin `acl` (grupos `basico` y `premium`)

|
| **Cuotas / Throttling** | Límite de peticiones por minuto según el producto

| Plugin `rate-limiting` (5 req/min vs 20 req/min)

|
| **Políticas de Caché** | Almacenamiento en memoria de consultas `GET` exitosas (`200`)

| Plugin `proxy-cache` (`TTL = 30s`)

|
| **Trazabilidad Distribuida** | Inyección de UUID por petición y cabeceras del Gateway

| Plugins `correlation-id` (`X-Correlation-ID`) y `response-transformer`

|

---

## 📂 3. Estructura del Proyecto

```
lab-apigateway/
├── gateway/
│   ├── Containerfile                  # Imagen de Kong 3.6 en modo DB-less
│   └── kong.yml                       # Configuración declarativa de servicios, rutas, plugins y consumidores
├── clientes-api/                      # Microservicio CRUD de Clientes (Quarkus + Panache + H2)
│   ├── Containerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/lab/clientes/
│       │   ├── Cliente.java           # Entidad Panache
│       │   ├── ClienteResource.java   # Endpoints CRUD + lectura de X-Consumer-Username y X-Correlation-ID
│       │   └── DataLoader.java        # Carga inicial de datos (Ana Torres, Luis Pérez)
│       └── resources/
│           ├── application.properties
│           └── META-INF/openapi.yaml  # Contrato OpenAPI 3.0.3 (Contract-First)
└── cuentas-api/                       # Microservicio de Cuentas (Quarkus + REST Client + Validator + H2)
    ├── Containerfile
    ├── pom.xml
    └── src/main/
        ├── java/com/lab/cuentas/
        │   ├── Cuenta.java            # Entidad Panache con @NotNull, @Pattern(PEN|USD) y @PositiveOrZero
        │   ├── ClientesClient.java    # MicroProfile REST Client hacia http://clientes-api:8080
        │   ├── CuentaResource.java    # Endpoints GET/POST con manejo de errores 400, 422 y 503
        │   └── DataLoader.java        # Carga inicial de 3 cuentas bancarias
        └── resources/
            └── application.properties

```

---

## 🛠️ 4. Stack Tecnológico y Dependencias Quarkus

* **Runtime:** Java 21 (Eclipse Temurin JRE 21 en contenedores)


* **Framework:** Quarkus (`rest-jackson`, `hibernate-orm-panache`, `jdbc-h2`, `smallrye-openapi`, `smallrye-health`, `rest-client-jackson`, `hibernate-validator`)


* **API Gateway:** Kong 3.6 (Declarative DB-less mode)


* **Contenedores y Red:** Podman (`lab-net`)



---

## 🚀 5. Guía de Despliegue Paso a Paso

### 5.1. Compilar y empaquetar los microservicios Quarkus

Ejecutar desde la raíz `lab-apigateway`:

```
# 1. Empaquetar clientes-api
cd clientes-api
./mvnw clean package -DskipTests
cd ..

# 2. Empaquetar cuentas-api
cd cuentas-api
./mvnw clean package -DskipTests
cd ..

```

### 5.2. Construir las imágenes con Podman

```
podman build -t clientes-api ./clientes-api
podman build -t cuentas-api ./cuentas-api
podman build -t api-gateway ./gateway

```

### 5.3. Crear la red aislada y desplegar los contenedores

```
# Crear red interna
podman network create lab-net

# Levantar microservicios de backend SIN publicar puertos al exterior
podman run -d --name clientes-api --network lab-net clientes-api
podman run -d --name cuentas-api --network lab-net cuentas-api

# Levantar Kong API Gateway publicando puertos 8000 (Proxy) y 8001 (Admin)
podman run -d --name api-gateway --network lab-net -p 8000:8000 -p 8001:8001 api-gateway

# Verificar que los 3 contenedores estén en estado Up
podman ps

```

---

## 🔐 6. Matriz de Productos, Consumidores y Reglas de Negocio

| Consumidor | Producto / Grupo ACL | Subscription Key (`Ocp-Apim-Subscription-Key`) | Rate Limit | APIs Permitidas |
| --- | --- | --- | --- | --- |
| **`app-movil`** | Producto Básico (`basico`)

| `movil-key-123`

| 5 llamadas / min

| `/api/v1/clientes` (Recibe `403` en Cuentas)

|
| **`app-web`** | Producto Premium (`premium`)

| `web-key-456`

| 20 llamadas / min

| `/api/v1/clientes` y `/api/v1/cuentas`

|
| **Público** | Sin autenticación

| *No requiere*

| Sin límite | `/contrato` (Especificación OpenAPI)

|

### Reglas de Negocio y Resiliencia en `cuentas-api`:

* **Validación de Entrada (`HTTP 400 Bad Request`):** `clienteId` obligatorio, `moneda` restringida por expresión regular a `PEN|USD`, y `saldo` mayor o igual a cero (`@PositiveOrZero`).


* **Verificación de Existencia de Cliente (`HTTP 422 Unprocessable Entity`):** Antes de persistir una cuenta, `cuentas-api` consulta internamente a `http://clientes-api:8080/clientes/{id}`. Si `clientes-api` retorna `404`, se traduce a un `422` de regla de negocio.


* **Tolerancia a Fallos (`HTTP 503 Service Unavailable`):** Si `clientes-api` se encuentra detenido o excede el timeout de conexión (`2000ms`), `cuentas-api` captura la excepción y responde `503` indicando `"clientes-api no disponible"`.


* **Caché de Lectura (`X-Cache-Status: Miss / Hit`):** Las peticiones `GET` a `/api/v1/cuentas` con respuesta `200` son almacenadas en memoria por Kong durante **30 segundos**.



---

## 🧪 7. Batería Automatizada de Pruebas (Verificación E2E)

Ejecuta el siguiente bloque en **Git Bash** para validar todos los escenarios de seguridad, caché, validación y resiliencia:

```
URL=http://localhost:8000/api/v1
MOVIL="Ocp-Apim-Subscription-Key: movil-key-123"
WEB="Ocp-Apim-Subscription-Key: web-key-456"

# 1. Aislamiento de red (El backend no es accesible directamente -> Connection refused)
curl http://localhost:8080/clientes

# 2. Seguridad Key-Auth (Sin key o key inválida -> HTTP 401 Unauthorized)
curl -i $URL/clientes
curl -i $URL/clientes -H "Ocp-Apim-Subscription-Key: key-falsa"

# 3. Control de Acceso ACL (Producto Básico intentando acceder a Cuentas -> HTTP 403 Forbidden)
curl -i $URL/cuentas -H "$MOVIL"

# 4. Acceso Producto Premium y filtrado por Query Param (-> HTTP 200 OK)
curl -i $URL/cuentas -H "$WEB"
curl -i "$URL/cuentas?clienteId=1" -H "$WEB"

# 5. Proxy Cache en el Gateway (Primera llamada: Miss | Segunda llamada: Hit)
curl -s -D - -o /dev/null $URL/cuentas/2 -H "$WEB" | grep -i x-cache-status
curl -s -D - -o /dev/null $URL/cuentas/2 -H "$WEB" | grep -i x-cache-status

# 6. Creación de cuenta válida (-> HTTP 201 Created) y demostración de caché activa
curl -s $URL/cuentas -H "$WEB"
curl -i -X POST $URL/cuentas -H "$WEB" -H "Content-Type: application/json" -d '{"clienteId": 1, "moneda": "USD", "saldo": 100.50}'
curl -s $URL/cuentas -H "$WEB"

# 7. Bean Validation: Moneda EUR e importe negativo (-> HTTP 400 Bad Request)
curl -i -X POST $URL/cuentas -H "$WEB" -H "Content-Type: application/json" -d '{"clienteId": 1, "moneda": "EUR", "saldo": -20}'

# 8. Comunicación Inter-Servicio: Cliente 999 inexistente (-> HTTP 422 Unprocessable Entity)
curl -i -X POST $URL/cuentas -H "$WEB" -H "Content-Type: application/json" -d '{"clienteId": 999, "moneda": "PEN", "saldo": 50}'

# 9. Resiliencia: Caída simulada de clientes-api (-> HTTP 503 Service Unavailable)
podman stop clientes-api
curl -i -X POST $URL/cuentas -H "$WEB" -H "Content-Type: application/json" -d '{"clienteId": 1, "moneda": "PEN", "saldo": 50}'
podman start clientes-api
sleep 6

# 10. Prueba de Regresión: Producto Básico sigue operando en Clientes (-> HTTP 200 OK)
curl -i $URL/clientes -H "$MOVIL"

# 11. Trazabilidad en Logs del Backend y Auditoría del Gateway
podman logs clientes-api | grep consumidor
curl -s http://localhost:8000/contrato
curl -s http://localhost:8001/acls
curl -s http://localhost:8001/services

```
