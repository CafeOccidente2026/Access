# CaféOccidente — Migración de Access a web

Monorepo: `cafe-occidente-frontend/` (Angular 22) y `cafeoccidente-backend/` (Spring Boot 4.1.1,
Java 21, PostgreSQL 18, Flyway). Un solo `README.md` y un solo `.gitignore`, ambos en la raíz.

## Fuentes de verdad

1. **RF v1.4 y HU v1.1** en `requerimientos_del_sistema/`. La carpeta está en `.gitignore`: existe
   solo en local. Mandan sobre el código y sobre Access.
2. **`requerimientos_del_sistema/analisis_estado_actual.md`**: inventario, matriz HU → estado,
   mapeo de BD, plan por fases y preguntas abiertas. Se actualiza la fila de la HU al terminarla.
3. **Access** es referencia para lo que el RF no cambia o no define. En el frontend las pantallas
   quedan lo más iguales posible a Access (distribución, campos, orden, botones, textos y flujo),
   salvo lo que el RF cambie explícitamente.
4. **`capturas/`** es la referencia visual de Access; `docs/legacy-vba-export/` tiene el VBA.
   Las dos carpetas son locales.
5. RF-22 Inventarios y RF-29 Contabilizado están pendientes de levantamiento: no se inventan reglas.
6. Si el RF es ambiguo o contradice otra fuente: preguntar, no suponer.

## Convenciones de código

- Identificadores en inglés (clases, métodos, variables, archivos, endpoints, DTOs). Textos
  visibles y comentarios en español.
- POO y responsabilidad única: una clase, una razón para cambiar.
- Backend, por módulo: `controller → service (interfaz) → service.impl → repository → entity →
  dto (record) → mapper`.
- Frontend: componentes standalone, `ChangeDetectionStrategy.OnPush`, signals y `toSignal(...)`.
  Sin `.subscribe()` manual en componentes.
- Sin textos fijos: cada pantalla lee su JSON de `public/assets/data/` con `ContentService`.
- Sin colores fijos: los tokens viven en `src/styles.css` (`@theme`). Esto incluye los
  generadores de PDF.
- Reusar `shared/ui/` y `shared/utils/` antes de crear algo nuevo.

## Base de datos (en español)

- Tablas, columnas, constraints, índices y secuencias en `snake_case`, sin tildes ni ñ.
- Tablas en plural. PK `id`. FK `<tabla_singular>_id` (ej. `agencia_id`, `tercero_id`).
- Prefijos de constraints: `pk_`, `fk_`, `uq_`, `ck_`; índices con `ix_`.
- Las entidades JPA siguen en inglés y mapean con `@Table(name = ...)` y `@Column(name = ...)`.
- Todo cambio de esquema es una migración Flyway nueva (`Vn__descripcion.sql`). Nunca se edita
  una ya aplicada. `ddl-auto: validate`.
- Excepción: el rebaseline del esquema en español de la fase 1, si se aprueba, reemplaza las
  migraciones actuales.

## Reglas de negocio transversales

- Ningún parámetro de negocio fijo en el código (125, 250, 70, 94, 0,8 %…): todo sale del
  Registro de Control de la agencia.
- La agencia y el rol se toman SIEMPRE del token o de la sesión del servidor, nunca del request.
  Para el Administrador manda la agencia de trabajo elegida con el botón Agencias (RF-05.1).
- Precios, costos, bonificación, retención, totales y consecutivos (factura, remisión, anuncio,
  Id de transacción) los calcula el servidor. Lo que mande el cliente en esos campos se ignora.
- Dinero y cantidades con `BigDecimal`, nunca `double` ni `float`. Los valores calculados se
  muestran con 2 decimales sin redondear (truncar). Pendiente (Q3): si el truncado aplica solo
  al mostrar o también al guardar, y si los montos en pesos llevan decimales. No asumir hasta
  que se defina.
- Roles: Administrador, Usuario y Operaciones (este último solo entra a Actualizar Anuncios).

## Git

- `main` NO se toca por ahora. Solo recibe un PR desde `develop` cuando un bloque esté
  terminado, probado y aprobado por el usuario.
- `develop` es la rama base de trabajo. Cada fase o historia va en su propia rama, creada desde
  `develop` actualizado: `feature/<fase-o-hu>-<descripcion-corta>`
  (ej. `feature/hu-01-login-roles`, `feature/bd-esquema-espanol`).
- Al terminar se abre un PR a `develop` con **merge commit** (no squash). Se esperan los checks en
  verde; el usuario lo revisa y hace el merge.
- Después del merge: `git switch develop && git pull`, borrar la rama local con `git branch -d`
  (nunca `-D`) y la remota. La siguiente rama sale de ese `develop` actualizado.
- `feature/daniers` y `feature/juan` no se modifican: quedan como respaldo.
- Commits pequeños y frecuentes: uno por cambio lógico, no uno gigante al final.
- Mensajes en español, imperativo, máximo ~60 caracteres, sin punto final y sin emojis.
  Ej.: "Toma la agencia del token al guardar compras", "Agrega tabla terceros".
  Cuerpo solo si hace falta explicar el porqué, en 1-2 líneas.
- NUNCA agregar `Co-Authored-By`, "Generated with Claude Code" ni ninguna mención a Claude o IA
  en commits, ramas ni PRs. No cambiar `git config user.name` ni `user.email`.

## Flujo de trabajo por historia

a. **Modo plan:** leer la HU, sus RF, su fila de la matriz y la captura de Access de la pantalla.
   Listar las dudas.
b. Esperar la aprobación del usuario.
c. Crear la rama.
d. Tests primero, a partir de los criterios de aceptación y de los ejemplos numéricos del RF.
e. Implementar en orden: BD → backend → frontend.
f. Correr los tests: backend `mvn -B package`; frontend `npx ng test --watch=false` y
   `npm run build`.
g. Actualizar la fila de la HU en la matriz del informe.
h. Commits y PR.

No se pasa a la siguiente historia hasta que la actual cumpla todos sus criterios de aceptación.

**Antes de cualquier cosa irreversible** (migraciones, borrar datos, renombrar, push, merge):
detenerse y pedir confirmación.

**Al terminar una prueba en el Chrome del usuario:** borrar los usuarios temporales y además
cerrar la sesión o borrar el localStorage de `localhost:4200`. Un token guardado de un usuario
ya borrado deja al navegador mandándolo en cada petición.

## Aprendizajes técnicos

- **Flyway:** Spring Boot 4.1.1 no trae autoconfiguración de Flyway. Las migraciones las corre
  `common/config/FlywayMigrationListener`, registrado en `META-INF/spring.factories` (no como
  `@Component`), sobre `ApplicationEnvironmentPreparedEvent`. Así corren antes de crear el
  datasource, también en `@SpringBootTest`.
- **`.gitignore`:** una entrada sin `/` inicial (`docs/`, `capturas`) ignora esa carpeta a
  cualquier profundidad. Con `/` inicial (`/docs/`) solo ignora la de la raíz. Como `docs/` está
  ignorada, los tests que leen CSV de Access desde ahí se saltan en CI (`Assumptions`).
- **Java 21:** el proyecto y el CI usan JDK 21 (`<java.version>21</java.version>`). En esta máquina
  el `java` por defecto es 25: usar el JDK 21 para compilar.
- **Zoneless:** Angular 22 sin `zone.js`. La UI solo se refresca con signals, `toSignal`, el pipe
  `async` o eventos de plantilla. Mutar un campo dentro de un callback no repinta.
- **CI:** frontend-ci hoy solo hace `npm ci && npm run build` (no corre tests); backend-ci corre
  `mvn -B package` contra un Postgres 18 de servicio.
