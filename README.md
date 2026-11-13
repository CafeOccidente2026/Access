# CaféOccidente — Migración de Access a Web

Migración del sistema de escritorio **AplicCompras** (Access) de la Cooperativa
CaféOccidente hacia una aplicación web moderna: frontend en Angular, backend en
Spring Boot, base de datos PostgreSQL.

Este repositorio contiene **dos proyectos independientes**, uno en cada carpeta:

```
Migracion de Access/
├── cafe-occidente-frontend/   Angular 22 + Tailwind CSS
└── cafeoccidente-backend/     Spring Boot 4 + PostgreSQL + Flyway (Docker)
```

Cada uno se instala y se corre por separado, como se explica abajo.

---

## Requisitos en tu PC

| Herramienta | Para qué | Obligatorio |
| --- | --- | --- |
| **Git** | Clonar y trabajar con el repositorio | Sí |
| **Node.js 22.22.3+** (o 24.15+/26+) y **npm 10+** | Correr el frontend | Sí, para el frontend |
| **Docker Desktop** | Levantar el backend + base de datos | Sí, para el backend |
| **JDK 21** | Solo si vas a abrir el backend en un IDE (VS Code, IntelliJ) para editar/depurar código Java | No — Docker ya trae su propio JDK adentro |

No necesitas instalar PostgreSQL en tu PC — corre dentro de un contenedor Docker.

---

## 1. Clonar el repositorio

```bash
git clone https://github.com/CafeOccidente2026/Access.git
cd Access
git checkout feature/tu-rama   # feature/daniers o feature/juan, segun corresponda
```

## 2. Levantar el **frontend**

```bash
cd cafe-occidente-frontend
npm install
npm start
```

Se abre en **http://localhost:4200** (entra directo a `/login`).

El frontend llama al backend en `/api` (ruta relativa). En local, `npm start` manda `/api` al backend del
**8090** con `proxy.conf.json`; en un servidor, lo hace el mismo proxy que sirve la web. Un solo build sirve
para cualquier ambiente.

Otros comandos útiles:
```bash
npm run build   # compila para producción (carpeta dist/)
npm test        # corre los tests
```

## 3. Levantar el **backend**

```bash
cd cafeoccidente-backend
cp .env.example .env
docker compose up --build
```

Abre tu `.env` recién creado y revisa los valores. Para desarrollo local, los de
`.env.example` sirven tal cual, pero puedes cambiar `DB_PASSWORD` si quieres.

El backend queda disponible en **http://localhost:8090** (el contenedor escucha
internamente en el 8080, pero se mapea al 8090 en tu PC para no chocar con otros
proyectos que ya usen el 8080).

### Usuario administrador inicial (bootstrap admin)

`ADMIN_USERNAME` y `ADMIN_PASSWORD` (en tu `.env`) son **las credenciales con las
que vas a entrar por primera vez** a `http://localhost:4200/login`. Son valores
que **cada persona define en su propio `.env`** — nunca se comparten ni se suben
a Git, y no tienen que coincidir entre los miembros del equipo.

Cómo funciona: cuando el backend arranca y no existe un usuario con ese nombre,
crea automáticamente un usuario con rol `ADMINISTRADOR` usando esos dos valores
(lo hace `AdminUserSeeder`). No hay que registrar nada a mano.

**Cada persona del equipo tiene su propia base de datos local** (su propio
Docker) — el admin de una persona no tiene ninguna relación con el admin de
otra. No hace falta coordinar ni compartir estos valores, salvo que quieran
hacerlo por comodidad propia.

⚠️ **Importante**: si cambias `ADMIN_PASSWORD` en tu `.env` **después** de que el
backend ya arrancó una vez (es decir, la tabla `users` ya no está vacía), ese
cambio **no se aplica solo**. Para que tome el valor nuevo hay que recrear la
base de datos:
```bash
docker compose down -v
docker compose up --build
```
El `-v` borra el volumen de Postgres y fuerza que todo se cree desde cero,
incluido el admin con el valor nuevo.

> **Nota**: hoy en día no existe todavía una pantalla para cambiar la
> contraseña del admin desde la propia interfaz — el único mecanismo para
> rotarla es el que se describe arriba (variable de entorno + recrear la base
> de datos). Cambiar la contraseña desde la interfaz queda pendiente de
> implementar.

### Cómo saber que quedó bien levantado

En los logs deberías ver, en este orden:
1. En el contenedor `postgres`: `database system is ready to accept connections`
2. En el contenedor `backend`:
   - Las migraciones de Flyway aplicándose (`Successfully applied 2 migrations...`)
   - `Tomcat started on port 8080`
   - `Started BackendApplication in X seconds`

Si después de `Started BackendApplication` ves alguna excepción, algo quedó mal
configurado — avisa antes de seguir.

Para bajar el backend cuando termines de trabajar:
```bash
docker compose down -v
```

---

## 4. Despliegue (producción)

Todo está en `deploy/`. Corre en cualquier Linux con Docker y Docker Compose. Solo Caddy queda
expuesto (80 y 443): sirve la web, manda `/api` al backend y hace el HTTPS. La base y el backend
no publican puertos.

**Requisitos:** Docker, el repositorio clonado y, con dominio público, los puertos 80 y 443
abiertos y el DNS apuntando al servidor.

**Primer arranque:**
```bash
cd deploy
cp .env.example .env      # llenar DOMINIO, TLS_MODO, claves y JWT_SECRET (no se sube a git)
docker compose -f docker-compose.prod.yml up -d --build
```
Flyway crea el esquema al arrancar el backend. Con `TLS_MODO=auto` Caddy pide el certificado a
Let's Encrypt; con `TLS_MODO=interno` (intranet sin dominio) usa uno propio y el navegador pide
aceptarlo la primera vez.

**Actualizar:**
```bash
git pull && cd deploy && docker compose -f docker-compose.prod.yml up -d --build
```

**Respaldos:** el servicio `backup` hace un `pg_dump -Fc` todos los días a las 23:00 (hora de
Colombia) en `CARPETA_RESPALDOS` (por defecto `deploy/respaldos/`) y borra los de más de
`RESPALDO_DIAS` días. Esa carpeta se debe copiar fuera del servidor. Respaldo manual:
```bash
docker compose -f docker-compose.prod.yml exec backup sh /respaldo.sh ahora
```

**Restaurar** (borra la base actual; pide escribir `SI`):
```bash
./restaurar.sh respaldos/cafeoccidente_AAAAMMDD_HHMM.dump
```

### Usuario de la base (seguridad)

La app no se conecta como superusuario. Hay tres usuarios, cada uno con su clave en `deploy/.env`:
- `DB_ADMIN_USER`: el superusuario del contenedor; solo administra.
- `FLYWAY_USER`: dueño del esquema; lo usan Flyway (al arrancar el backend) y los respaldos.
- `DB_USER`: la app; lee y escribe filas, sin crear, cambiar ni borrar tablas.

`deploy/roles-bd.sql` crea los dos últimos y deja los permisos. En una base **nueva** lo corre solo el
primer arranque (`deploy/initdb/`). En una base que **ya existe** (con el superusuario de antes):
1. Respaldo: `docker compose -f docker-compose.prod.yml exec backup sh /respaldo.sh ahora`.
2. Crear los roles (queda igual si se corre dos veces):
   ```bash
   docker compose -f docker-compose.prod.yml cp roles-bd.sql postgres:/tmp/roles-bd.sql
   docker compose -f docker-compose.prod.yml exec postgres psql -U <usuario actual> -d <base> -v ON_ERROR_STOP=1 \
     -v migrador=cafeoccidente_migrador -v clave_migrador='<clave>' \
     -v app=cafeoccidente_app -v clave_app='<clave>' -f /tmp/roles-bd.sql
   ```
3. En `deploy/.env`: `DB_ADMIN_USER`/`DB_ADMIN_PASSWORD` = el usuario y clave de antes; `FLYWAY_USER`/
   `FLYWAY_PASSWORD` y `DB_USER`/`DB_PASSWORD` = los roles nuevos.
4. `docker compose -f docker-compose.prod.yml up -d` y revisar en el log del backend que arranca sin errores.

Sin `FLYWAY_USER` el backend migra con `DB_USER`, como antes.

### Impresión directa de facturas (en cada equipo de caja)

Las facturas (Seco, Verde, Pasilla, Cupos y Anunciadas), la reimpresión de Consultar Compras y la
remisión de Registrar Salidas se mandan a imprimir sin abrir otra pestaña. Una página web no puede
saltarse el cuadro de impresión por su cuenta; Chrome (o Edge) sí lo hace si se abre con la bandera
`--kiosk-printing`. Los informes siguen saliendo en la vista previa de la pantalla.

En cada equipo:
1. **Impresora predeterminada:** en Windows, Configuración → Impresoras, dejar como predeterminada la
   impresora de facturas.
2. **Acceso directo:** crear un acceso directo en el escritorio con este destino (ajustar la ruta de
   Chrome y la dirección del servidor):
   ```
   "C:\Program Files\Google\Chrome\Application\chrome.exe" --kiosk-printing --app=https://SERVIDOR
   ```
   `--app` la abre como ventana propia de la aplicación; se puede quitar si se prefiere una pestaña normal.
3. **Primera impresión:** abrir Chrome normal (sin la bandera), imprimir una factura de prueba y en el
   cuadro dejar papel, márgenes «Ninguno» y escala 100 %. Chrome recuerda esa configuración.
4. **Cerrar Chrome del todo** (también el icono de la bandeja) antes de usar el acceso directo: la
   bandera solo vale si Chrome arranca con ella.

Sin la bandera todo sigue funcionando: aparece el cuadro de impresión normal en la misma pestaña.

---

## Estado actual del proyecto

### Frontend
Cubre el **diseño y la estructura** de todas las pantallas migradas desde Access
(login, menús, los 4 formularios de compra, compras a futuro, inventarios,
consulta). El login y la pantalla de Usuarios ya están conectados al backend real
(autenticación JWT); el resto de pantallas sigue siendo maqueta sin conectar.

### Backend
Es un **esqueleto por capas**: existen todos los paquetes, clases y endpoints
planeados para cada módulo (`controller`, `service`, `service.impl`, `repository`,
`entity`, `dto`, `mapper`), pero **todavía no tienen lógica de negocio real** salvo
el módulo `users` (ver tabla abajo). Las demás entidades solo tienen el campo `id`,
los controllers/services están vacíos, y las tablas de la base de datos son
placeholders (una tabla por entidad, sin columnas propias todavía).

El objetivo de este esqueleto es fijar la arquitectura y los nombres de paquetes
antes de implementar la lógica, para que el equipo se reparta los módulos sin
pisarse.

| Módulo | Representa | Pendiente |
| --- | --- | --- |
| `controlrecord` | Registro de control (parámetros generales) | Campos, reglas de negocio, endpoints |
| `purchases.drycoffee` | Compras de café seco | Campos de la entidad, validaciones, DTOs |
| `purchases.greencoffee` | Compras de café verde | Ídem |
| `purchases.othercoffee` | Compras de otros cafés | Ídem |
| `purchases.husk` | Compra de pasilla | Ídem |
| `purchases.future` | Anuncios, cupos y compras a futuro | Relaciones entre entidades, reglas de cupos |
| `purchases.shared` | Catálogos: Agencia, Fondo, Código de producto | Campos y uso desde los demás módulos |
| `inventory` | Conductores, remisiones, movimientos | Relaciones con compras |
| `users` | Usuarios, roles, autenticación | Implementado: login/refresh JWT, alta/listado/baja de usuarios, bootstrap del admin inicial. Pendiente: cambio de contraseña desde la interfaz |
| `common` | Seguridad, CORS, OpenAPI, manejo de errores | `SecurityConfig`, `JwtService` y CORS implementados; OpenAPI pendiente |

### Base de datos
- **Desarrollo**: PostgreSQL en Docker, sin conexión a Aurora.
- **Migraciones**: versionadas con Flyway en
  `cafeoccidente-backend/src/main/resources/db/migration/`. Cualquier cambio de
  esquema es un archivo nuevo `Vx__descripcion.sql`, nunca se edita uno ya
  aplicado.
- **Producción (Aurora)**: cuando se despliegue, no se toca código — solo se
  cambian las variables de conexión (`DB_URL`, `DB_USER`, `DB_PASSWORD`) al
  perfil `prod`. Flyway aplica las mismas migraciones contra Aurora la primera
  vez que arranque ahí.
- Nota técnica: Spring Boot 4.1.1 no trae autoconfiguración propia de Flyway,
  así que las migraciones se disparan manualmente en
  `BackendApplication.main()` antes de que arranque el contexto de Spring.

---

## Convenciones de trabajo

### Frontend (Angular)
1. **Código en inglés, comentarios en español.** Clases, variables, métodos,
   selectores y archivos en inglés; comentarios puntuales (no narrativos) en
   español.
2. **Cero texto "quemado".** Todo texto visible sale de un JSON en
   `cafe-occidente-frontend/public/assets/data/`, uno por pantalla, cargado con
   `ContentService.loadJson<T>('nombre-archivo')`. Para cambiar un texto se
   edita el JSON, no el componente.
3. **Cero color "quemado".** La paleta vive centralizada en
   `cafe-occidente-frontend/src/styles.css` (`@theme`). Un color nuevo se
   agrega ahí, con su token, y se reutiliza por nombre.
4. **Componentes standalone + `OnPush` + `toSignal`.** Los datos de
   `ContentService` se consumen con `toSignal(...)`, nunca con `.subscribe()`
   manual.
5. **Reusar antes que crear.** Revisar `shared/ui/` antes de escribir un
   componente o estilo nuevo.

### Backend (Spring Boot)
1. **Monolito modular por dominio.** Cada módulo (`controlrecord`,
   `purchases.*`, `inventory`, `users`) agrupa sus propias capas (`controller`,
   `service`, `service.impl`, `repository`, `entity`, `dto`, `mapper`).
2. **Nunca editar el esquema a mano.** Todo cambio de base de datos es una
   migración nueva de Flyway.
3. **`ddl-auto: validate`.** Hibernate nunca crea/modifica tablas solo; si
   agregas un campo a una entidad, agrega también la columna en una migración.
4. **Código en inglés, comentarios en español**, igual que el frontend.

### Git
- Existe **un solo `.gitignore`** y **un solo `README.md`**, ambos en esta
  raíz — no se crean `.gitignore`/`README.md` dentro de
  `cafe-occidente-frontend/` ni `cafeoccidente-backend/`.
- **Nunca se sube**: `node_modules/`, `target/`, `dist/`, `.angular/`, `.env`,
  `.claude/`, `capturas/`. Ya están todos en el `.gitignore`.
- Cada quien crea su propio `.env` local a partir de `.env.example` (dentro de
  `cafeoccidente-backend/`) — nunca se sube el `.env` real.
- **`main` está protegida** mediante un Ruleset de GitHub: no se permite push
  directo, todo cambio entra vía Pull Request, y requiere que pasen los checks
  automáticos de CI (frontend y backend, ver abajo) antes de poder mezclar.
- **Ramas de trabajo**: `feature/daniers` y `feature/juan`. Cada quien trabaja
  en la suya, y cuando tiene un avance listo:
  ```bash
  git add -A
  git commit -m "feat: descripcion del cambio"
  git push
  ```
  y abre el Pull Request hacia `main` desde GitHub.

### CI (verificación automática)
En cada Pull Request corren dos workflows de GitHub Actions
(`.github/workflows/`):
- **Frontend CI**: instala dependencias y compila el frontend (`npm ci && npm run build`).
- **Backend CI**: levanta un Postgres real como servicio y arranca el backend
  completo contra él (igual que en local con Docker), validando que las
  entidades, migraciones de Flyway y el contexto de Spring carguen sin errores.

Si alguno falla, el botón de mezclar el PR queda bloqueado automáticamente.

---

## Estructura del frontend

```
cafe-occidente-frontend/
├── public/assets/
│   ├── data/       Un .json por pantalla: todos los textos y valores editables
│   └── images/     Logo y recursos gráficos
└── src/app/
    ├── core/       models/ (interfaces compartidas) y services/ (ContentService, NavigationService)
    ├── shared/ui/  Componentes reutilizables (botones, ventanas, formularios, paneles)
    └── features/   Una carpeta por pantalla
```

| Pantalla (Access) | Ruta | JSON de contenido |
| --- | --- | --- |
| AplicCompras Login | `/login` | `login.json` + `shell.json` |
| Acceso Principal | `/acceso-principal` | `main-access.json` |
| Usuarios (solo ADMIN) | `/usuarios` | `user-management.json` |
| Menú Principal | `/menu-principal` | `main-menu.json` |
| Registro de Control | `/registro-control` | `control-record.json` |
| Compras (menú) | `/compras` | `purchases-menu.json` |
| Compras Café Seco | `/compras/cafe-seco` | `purchase-form-dry.json` |
| Compras Cafés Otros | `/compras/cafe-otros` | `purchase-form-other.json` |
| Compras Café Verde | `/compras/cafe-verde` | `purchase-form-green.json` |
| Compra Pasilla | `/compras/pasilla` | `purchase-form-husk.json` |
| Menú Compras a Futuro | `/compras/futuro` | `future-purchases-menu.json` |
| Menú Inventarios | `/compras/inventarios` | `inventory-menu.json` |
| Consultar Compras | `/compras/consulta` | `purchase-query.json` |

## Estructura del backend

```
cafeoccidente-backend/
├── .env.example / docker-compose.yml / Dockerfile / pom.xml
└── src/main/
    ├── java/com/cafeoccidente/backend/
    │   ├── common/            Config, seguridad JWT, manejo de errores
    │   ├── controlrecord/     Parámetros generales de compras
    │   ├── purchases/
    │   │   ├── drycoffee/ othercoffee/ greencoffee/ husk/
    │   │   ├── future/        Anuncios, cupos, compras a futuro
    │   │   └── shared/        Agencia, Fondo, Código de producto
    │   ├── inventory/         Conductores, remisiones, movimientos
    │   └── users/             Usuarios, roles, autenticación
    └── resources/
        ├── application.yml / application-dev.yml / application-prod.yml
        └── db/migration/      Migraciones versionadas de Flyway
```