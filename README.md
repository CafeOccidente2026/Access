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
   - Las migraciones de Flyway aplicándose (`Successfully applied N migrations...`)
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
(login, menús, los 5 formularios de compra, compras a futuro, inventarios,
consulta). Conectadas al backend real: Login, Usuarios, Registro de Control,
Actualizar Anuncio (Corriente y Pasilla), Compras Café Seco, Compras Café Verde,
Compra Pasilla, Compras Cafés Otros, Compras a Futuro, Fertifuturo (pantalla
nueva), Asignar Cupo por anuncio, Registrar Salidas/Remisión, Reporte de
Inventario (consulta de solo lectura), y las dos variantes de cupo de Café Seco
(Compra por Cupo / Facturación por Cupo). **Siguen sin conectar** (maqueta o
placeholder): Consulta de Compras general, "Reimprimir Remisión" e "Ingresar
Conductores" (esta última quedó obsoleta: `Driver` se eliminó en Fase 3, la
opción del menú de Inventarios sigue apuntando a una pantalla vacía).

### Backend
Los 5 módulos de compra (Café Seco, Café Verde, Pasilla, Cafés Otros,
Fertifuturo) tienen lógica de negocio real y probada, con la cascada de cálculo
migrada del VBA original (ver
`docs/informe-formulas-compras-vs-vba.md`). También están implementados: Cupos
por anuncio (`AnnouncementQuota`), anuncios compartidos entre agencias
(`docs/diseno-anuncios-compartidos.md`), Compras a Futuro, e Inventarios
(movimiento automático por compra + Remisiones/Salidas).

| Módulo | Representa | Estado |
| --- | --- | --- |
| `controlrecord` | Registro de control (parámetros generales) | Implementado |
| `purchases.drycoffee` | Compras de café seco | Implementado (cascada VBA completa, validación de negativos, tope de cupo). Falta: generación del PDF del documento soporte (si no está ya cubierta por el módulo de facturación) |
| `purchases.greencoffee` | Compras de café verde | Implementado |
| `purchases.othercoffee` | Compras de otros cafés | Implementado, frontend conectado |
| `purchases.husk` | Compra de pasilla | Implementado |
| `purchases.future` | Anuncios compartidos, Cupos por anuncio, Compras a Futuro, Fertifuturo | Implementado, frontend conectado. Sistema de "Obligación" de Fertifuturo dejado fuera a propósito (ver `docs/informe-formulas-compras-vs-vba.md`) |
| `purchases.shared` | Catálogos: Agencia, Fondo, Código de producto, Caficultor (Grower) | Implementado |
| `inventory` | Movimientos de inventario (automáticos por compra) y Remisiones/Salidas | Implementado, frontend conectado (consulta de movimientos + registrar salidas) |
| `users` | Usuarios, roles, autenticación | Implementado: login/refresh JWT, alta/listado/baja de usuarios, bootstrap del admin inicial |
| `common` | Seguridad, CORS, OpenAPI, manejo de errores, validación de negativos (`MoneyValidation`) | `SecurityConfig`, `JwtService`, CORS y `MoneyValidation` implementados; OpenAPI pendiente |

### Tests
- **Backend**: `cd cafeoccidente-backend && mvn test` (o vía Docker, ver
  `docs/informe-formulas-compras-vs-vba.md` para el comando exacto). Cubre los 5
  calculadores de compra (incluida la validación reproducible contra datos
  históricos reales de Café Seco), Cupos, Inventarios/Remisiones, Compras a
  Futuro y Anuncios compartidos.
- **Frontend**: `cd cafe-occidente-frontend && npm test`. Cubre la lógica pura
  compartida por los formularios de compra (formato/parseo de números, bloqueo
  secuencial de captura) y, a nivel de componente, el gate de campos
  requeridos que dispara la cascada de cálculo (Café Seco, Pasilla, Cafés
  Otros, Fertifuturo) — donde vivía el bug del "celular bloqueado" corregido
  el 2026-09-23 (ver `docs/informe-formulas-compras-vs-vba.md`) — además de
  Compras a Futuro, Asignar Cupo, Reporte de Inventario y Registrar Salidas.

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

### Migración del formulario "Compras Café Seco"

Las 4 variables del VBA original que estaban sin mapear ya están resueltas en
`DryCoffeePurchaseCalculator` (confirmadas contra las propiedades del formulario
Access):

- `Texto164` = `PorcKgPasProm` de RegControl → `ControlRecord.avgHuskPercentage`
  (fórmula `var4` del precio unitario en `Sacos_LostFocus`).
- `Texto176` = `BaseCarga` de RegControl → `ControlRecord.baseLoad`
  (`Pr_Base_PC = vrcps - (Costos * BaseCarga)`, calculado ahora en el servidor).
- `Texto105` / `Texto107` = acumulado mensual del caficultor (por cédula) que en
  Access calculaba la macro `CalculoReteFteMes`: suma de `Vr_Bruto` y de
  `Retefuente` de las compras del mes en curso, antes de esta transacción
  (`DryCoffeePurchaseRepository.sumMonthlyTotalsByIdNumber`). La Retefuente de la
  compra nueva se calcula sobre ese acumulado y se le descuenta lo ya practicado
  en el mes.
  - **A revisar con el negocio:** hoy el acumulado solo suma compras del módulo
    `drycoffee`. Cuando existan `othercoffee` / `greencoffee` / `husk` habrá que
    decidir si el acumulado mensual debe incluirlas también.
- `Pr_AlmDefec` (precio almendra defectuosa, factor `var4` de `Sacos_LostFocus`):
  el VBA nunca le asigna un valor real (solo `= 0` en los resets) y la pantalla
  migrada no tiene campo para capturarlo. Se guarda como
  `ControlRecord.defectiveAlmondUnitPrice`, sembrado en **0** y marcado `TODO`
  (migración `V6`). Con 0, `var4 = 0`, igual que hoy en Access.

El diseño de la pantalla (`shared/ui/purchase-form-view` + `purchase-form-dry.json`)
no se toca: la lógica (autollenado del anuncio, cascada, bloqueo secuencial,
Escape, botón Imprimir, guardado y aviso al cerrar) se conecta con
`@Input`/eventos añadidos a los componentes compartidos existentes.

Pendientes del mismo formulario (no solicitados aún): la búsqueda del
caficultor vía DLL externa / `pcompras` (hoy los datos del caficultor viven en
`Grower`, cargado por `scripts/migrate_eltambo.py`), y el split de pago
multi-instrumento. El tope de cupo por caficultor (`GrowerService.checkQuota`)
y el bloqueo de pertenencia a programa (`requireProgramMembership`, usado hoy
por Compras a Futuro) ya están implementados — ver
`docs/informe-formulas-compras-vs-vba.md` para el alcance real de datos
(`staging_legacy_ness`, solo 3 de ~20 Especiales tienen cobertura migrada).

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
