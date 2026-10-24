# Guía para compilar Quarkus y levantar el laboratorio con Podman

Esta guía documenta, en el orden correcto, los comandos utilizados para compilar `clientes-api`, construir las imágenes y levantar `clientes-api` junto con Kong API Gateway mediante Podman instalado directamente en Windows.

## 1. Estructura esperada

Ejecutar los comandos desde:

```text
C:\Dev\Quarkus\lab-apigateway\labGatewayNTT
```

La carpeta debe contener:

```text
labGatewayNTT
├── clientes-api
└── gateway
    ├── Containerfile
    └── kong.yml
```

En CMD se usa `dir`, no `ls`:

```cmd
dir
```

---

## 2. Verificar las herramientas

```cmd
java --version
mvn --version
clientes-api\mvnw.cmd --version
podman --version
```

Para este proyecto, Maven y Maven Wrapper deben usar Java 21. Se puede comprobar en la salida de `mvn --version` y `clientes-api\mvnw.cmd --version`.

---

## 3. Inicializar Podman por primera vez

Este paso se ejecuta una sola vez por instalación:

```cmd
podman machine init
```

Luego se inicia la máquina Linux administrada por Podman:

```cmd
podman machine start
```

Validar que Podman esté operativo:

```cmd
podman machine list
podman info
```

> En los siguientes días normalmente solo será necesario ejecutar `podman machine start`.

---

## 4. Compilar el proyecto Quarkus

Desde `labGatewayNTT`:

```cmd
cd clientes-api
mvnw.cmd clean package -DskipTests
cd ..
```

La compilación debe finalizar con:

```text
BUILD SUCCESS
```

Verificar el artefacto generado:

```cmd
if exist clientes-api\target\quarkus-app\quarkus-run.jar (echo QUARKUS OK) else (echo ERROR: FALTA COMPILAR)
```

Resultado esperado:

```text
QUARKUS OK
```

---

## 5. Construir la imagen de `clientes-api`

```cmd
podman build --tls-verify=false -f clientes-api\src\main\docker\Dockerfile.jvm -t localhost/clientes-api:latest clientes-api
```

El parámetro `--tls-verify=false` se utilizó por el problema de certificados del entorno de laboratorio. No se recomienda como configuración permanente para producción.

Verificar la imagen:

```cmd
podman image ls
```

Debe aparecer:

```text
localhost/clientes-api    latest
```

---

## 6. Construir la imagen del API Gateway

```cmd
podman build --tls-verify=false -f gateway\Containerfile -t localhost/api-gateway:latest gateway
```

Verificar las dos imágenes:

```cmd
podman image ls
```

Deben aparecer:

```text
localhost/clientes-api    latest
localhost/api-gateway     latest
```

La advertencia sobre `HEALTHCHECK` y el formato OCI no impide construir la imagen del gateway.

---

## 7. Crear la red de contenedores

Crear la red una sola vez:

```cmd
podman network create lab-net
```

Si ya existe, no es necesario volver a crearla. Se puede comprobar con:

```cmd
podman network ls
```

---

## 8. Eliminar contenedores anteriores

Antes de recrearlos:

```cmd
podman rm -f clientes-api api-gateway 2>NUL
```

Este comando no elimina las imágenes. Solo elimina contenedores anteriores con esos nombres.

---

## 9. Levantar el backend Quarkus

El backend se ejecuta sin publicar el puerto `8080`. Solo será accesible para Kong mediante `lab-net`:

```cmd
podman run -d --name clientes-api --network lab-net localhost/clientes-api:latest
```

Verificar su estado:

```cmd
podman ps -a
podman logs clientes-api
```

El contenedor debe permanecer en estado `Up`. En los logs, Quarkus debe indicar que escucha en `0.0.0.0:8080`.

---

## 10. Levantar Kong API Gateway

```cmd
podman run -d --name api-gateway --network lab-net -p 8000:8000 -p 8001:8001 localhost/api-gateway:latest
```

Puertos publicados:

- `8000`: proxy de APIs.
- `8001`: administración de Kong.

Verificar los contenedores:

```cmd
podman ps -a
```

Deben aparecer en estado `Up`:

```text
clientes-api
api-gateway
```

Revisar los logs del gateway:

```cmd
podman logs api-gateway
```

---

## 11. Validar la comunicación interna

Comprobar la red:

```cmd
podman network inspect lab-net
```

Comprobar que Kong puede resolver al backend por nombre:

```cmd
podman exec api-gateway getent hosts clientes-api
```

Kong usa el nombre del contenedor en esta URL interna:

```text
http://clientes-api:8080
```

No hace falta publicar `8080` en Windows porque ambos contenedores están conectados a `lab-net`.

---

## 12. Probar el gateway

### Contrato público, sin subscription key

```cmd
curl.exe -i http://localhost:8000/contrato
```

### API protegida, sin key

```cmd
curl.exe -i http://localhost:8000/api/v1/cuentas
```

Resultado esperado: `401 Unauthorized`.

### Plan Básico

```cmd
curl.exe -i -H "Ocp-Apim-Subscription-Key: movil-key-123" http://localhost:8000/api/v1/cuentas
```

### Plan Premium

```cmd
curl.exe -i -H "Ocp-Apim-Subscription-Key: web-key-456" http://localhost:8000/api/v1/cuentas
```

En las respuestas exitosas deben aparecer los encabezados configurados por Kong, entre ellos:

```text
X-Correlation-ID
X-Gateway: kong-lab
```

---

## 13. Comandos diarios para volver a levantar el laboratorio

Si las imágenes ya existen y el código no cambió:

```cmd
podman machine start
podman rm -f clientes-api api-gateway 2>NUL
podman run -d --name clientes-api --network lab-net localhost/clientes-api:latest
podman run -d --name api-gateway --network lab-net -p 8000:8000 -p 8001:8001 localhost/api-gateway:latest
podman ps -a
```

Si la máquina ya está iniciada, Podman puede indicar que ya está ejecutándose; se puede continuar con los demás comandos.

---

## 14. Flujo completo cuando cambia el código

```cmd
cd clientes-api
mvnw.cmd clean package -DskipTests
cd ..
podman build --tls-verify=false -f clientes-api\src\main\docker\Dockerfile.jvm -t localhost/clientes-api:latest clientes-api
podman build --tls-verify=false -f gateway\Containerfile -t localhost/api-gateway:latest gateway
podman rm -f clientes-api api-gateway 2>NUL
podman run -d --name clientes-api --network lab-net localhost/clientes-api:latest
podman run -d --name api-gateway --network lab-net -p 8000:8000 -p 8001:8001 localhost/api-gateway:latest
podman ps -a
```

---

## 15. Detener el laboratorio

Detener los contenedores:

```cmd
podman stop api-gateway clientes-api
```

Detener la máquina Podman cuando ya no se necesite:

```cmd
podman machine stop
```

---

## 16. Diagnóstico rápido

### Ver todos los contenedores

```cmd
podman ps -a
```

### Ver logs del backend

```cmd
podman logs clientes-api
```

### Ver logs del gateway

```cmd
podman logs api-gateway
```

### Ver imágenes

```cmd
podman image ls
```

### Ver redes

```cmd
podman network ls
```

### Error: intenta descargar `docker.io/library/clientes-api`

Significa que la imagen local no existe o se usó un nombre corto incorrecto. Construirla y ejecutar el nombre completo:

```cmd
podman build --tls-verify=false -f clientes-api\src\main\docker\Dockerfile.jvm -t localhost/clientes-api:latest clientes-api
podman run -d --name clientes-api --network lab-net localhost/clientes-api:latest
```

### Error: no puede conectarse al socket de Podman

```cmd
podman machine start
podman info
```

### Error: un contenedor aparece como `Exited`

```cmd
podman ps -a
podman logs clientes-api
podman logs api-gateway
```

---

## Orden conceptual

```text
Compilar Quarkus
    -> construir imagen clientes-api
    -> construir imagen api-gateway
    -> crear o reutilizar lab-net
    -> ejecutar clientes-api
    -> ejecutar api-gateway
    -> probar por localhost:8000
```
