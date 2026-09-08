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
```

## 2. Levantar el **frontend**

```bash
cd cafe-occidente-frontend
npm install
npm start
```

Se abre en **http://localhost:4200** (entra directo a `/login`).

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

Abre `.env` y revisa los valores (para desarrollo local, los de `.env.example` sirven
tal cual; puedes cambiar `DB_PASSWORD` si quieres). Revisa también `ADMIN_USERNAME`
y `ADMIN_PASSWORD`: son las credenciales del primer usuario administrador, que se
crea automáticamente la primera vez que arranca el backend (tabla `users` vacía).
**Cambia esa contraseña desde la pantalla de Usuarios apenas entres por primera
vez** — el arranque solo la usa una vez, no la vuelve a pedir en arranques
siguientes.

El backend queda disponible en **http://localhost:8090** (el contenedor escucha
internamente en el 8080, pero se mapea al 8090 en tu PC para no chocar con otros
proyectos que ya usen el 8080).

**Cómo saber que quedó bien levantado** — en los logs deberías ver, en este orden:
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

## Estado actual del proyecto

### Frontend
Cubre el **diseño y la estructura** de todas las pantallas migradas desde Access
(login, menús, los 4 formularios de compra, compras a futuro, inventarios,
consulta). El login y la pantalla de Usuarios ya están conectados al backend real
(autenticación JWT); el resto de pantallas sigue siendo maqueta sin conectar.

### Backend
Es un **esqueleto por capas**: existen todos los paquetes, clases y endpoints
planeados para cada módulo (`controller`, `service`, `service.impl`, `repository`,
`entity`, `dto`, `mapper`), pero **todavía no tienen lógica de negocio real**. Las
entidades solo tienen el campo `id`, los controllers/services están vacíos, y las
tablas de la base de datos son placeholders (una tabla por entidad, sin columnas
propias todavía).

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
| `users` | Usuarios, roles, autenticación | Implementado: login/refresh JWT, alta/listado/baja de usuarios, bootstrap del admin inicial |
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
    │   └── users/             Usuarios, roles, permisos, auth
    └── resources/
        ├── application.yml / application-dev.yml / application-prod.yml
        └── db/migration/      Migraciones versionadas de Flyway
