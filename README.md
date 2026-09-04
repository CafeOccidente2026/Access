# CaféOccidente - Frontend de Compras (Angular 22)

Migración del frontend del sistema de escritorio **AplicCompras** (Access) a una
aplicación web con **Angular 22** y **Tailwind CSS v4**.

> Este entregable cubre únicamente el **diseño y la estructura** de las pantallas.
> La conexión con el backend (Java) y la lógica de negocio se implementarán en una
> siguiente etapa.

## Cómo clonar y ejecutar el proyecto

```bash
git clone https://github.com/CafeOccidente2026/Access.git
cd Access/cafe-occidente-frontend
npm install
npm start
```

Esto levanta el servidor de desarrollo en `http://localhost:4200/`. La app entra
directo a `/login`.

### Requisitos

- Node.js 22.22.3+ (o 24.15+ / 26+)
- npm 10+

### Otros comandos útiles

```bash
npm run build   # compila para producción (carpeta dist/)
npm run watch   # build en modo watch (desarrollo)
npm test        # corre los tests (Vitest)
```

> Todo el código vive dentro de `cafe-occidente-frontend/`. La carpeta `capturas/`
> en la raíz del repo es solo material de referencia visual (capturas de Access)
> que **no** se sube a git (está en `.gitignore`); no forma parte de la app.

## Reglas de trabajo del proyecto

Estas son las convenciones que seguimos en todo el código, no solo sugerencias:

1. **Código en inglés, comentarios en español.** Nombres de clases, variables,
   métodos, selectores y archivos van en inglés (`FuturePurchaseFormComponent`,
   `onAccept()`, `quota-assignment.ts`). Los comentarios (los `/** ... */` sobre
   cada clase, o alguna línea puntual donde algo no sea obvio) van en español.
   No se traduce el código, solo se explica en comentarios cuando hace falta.
2. **Cero texto "quemado" en el código.** Ningún componente escribe literales
   como `"Aceptar"`, `"Agencia"` o un valor de ejemplo directo en el `.html`/`.ts`.
   Todo texto visible (títulos, etiquetas, opciones de menú, valores de muestra)
   sale de un archivo JSON en **`cafe-occidente-frontend/public/assets/data/`**,
   uno por pantalla, cargado con `ContentService.loadJson<T>('nombre-archivo')`.
   Si hay que cambiar un texto o agregar un campo, se edita el JSON — no el
   componente. Ver [¿Dónde está el JSON de cada pantalla?](#dónde-está-el-json-de-cada-pantalla).
3. **Cero color "quemado" en las plantillas.** No se escriben hex ni clases de
   color de Tailwind por defecto (`bg-blue-500`, `#3fb6c4`, etc.) sueltos en un
   `.html` para representar la identidad visual del sistema. La paleta completa
   vive centralizada en **`cafe-occidente-frontend/src/styles.css`**, dentro del
   bloque `@theme` (variables `--color-panel-sky`, `--color-btn-face`, etc.), y
   se consume como clases Tailwind generadas a partir de esos tokens
   (`bg-panel-sky`, `text-shell-titlebar`, `border-btn-border`...). Un color
   nuevo se agrega ahí, con su token, y se reutiliza por nombre.
4. **Componentes standalone + `OnPush` + `toSignal`.** Todo componente es
   `standalone: true` con `changeDetection: ChangeDetectionStrategy.OnPush`.
   Los datos que vienen de `ContentService` (un `Observable`) se consumen con
   `toSignal(...)`, nunca con `.subscribe()` manual (evita fugas de suscripción
   y descontrol del ciclo de detección de cambios).
5. **Reusar antes que crear.** Antes de escribir un componente o estilo nuevo,
   revisar `shared/ui/` — botones, ventanas, filas de campos, grilla de menú,
   panel de formas de pago, etc. ya están hechos y se reutilizan en todas las
   pantallas que los necesitan.

## Estructura del proyecto

```
cafe-occidente-frontend/
├── public/
│   └── assets/
│       ├── data/       Un .json por pantalla: todos los textos y valores editables
│       └── images/     Logo y recursos gráficos estáticos
└── src/
    ├── index.html, main.ts   Punto de entrada de Angular
    ├── styles.css             Paleta de colores centralizada (@theme) + estilos base
    └── app/
        ├── app.ts             Componente raíz (solo <router-outlet>)
        ├── app.config.ts      Providers globales (HttpClient, Router)
        ├── app.routes.ts      Mapa de rutas: una entrada por pantalla
        ├── core/              Infraestructura transversal, sin UI
        │   ├── models/        Interfaces TypeScript compartidas (ver abajo)
        │   └── services/      ContentService y NavigationService (ver abajo)
        ├── shared/ui/         Componentes visuales reutilizables (ver abajo)
        └── features/          Una carpeta por pantalla o grupo de pantallas
```

### `core/models/` — interfaces compartidas

| Archivo | Qué define |
| --- | --- |
| `menu-option.model.ts` | Forma de un botón de menú (`label`, `route`, `emphasis`) |
| `form-field.model.ts` | Forma declarativa de un campo de formulario (`key`, `label`, `type`, `value`, `options`...) |
| `window-config.model.ts` | Config de la barra de título tipo ventana de escritorio |
| `payment-method.model.ts` | Forma del panel "Formas de Pago" y sus métodos |
| `purchase-form-content.model.ts` | Forma genérica del contenido de un formulario de compra (todas las secciones posibles) |
| `index.ts` | Re-exporta todo lo anterior, para importar desde `core/models` en un solo `import` |

### `core/services/` — infraestructura

| Archivo | Qué hace |
| --- | --- |
| `content.service.ts` | Única puerta de entrada a `public/assets/data/*.json` vía `HttpClient`. Ninguna pantalla llama `HttpClient` directo. |
| `navigation.service.ts` | Centraliza la navegación (`goTo(route)`), para no acoplar los componentes de presentación al `Router` de Angular. |

### `shared/ui/` — componentes reutilizables

| Componente | Uso |
| --- | --- |
| `access-window/` | Ventana interna estilo Access (barra de título + botón de cierre `X` funcional). Envuelve casi toda pantalla. |
| `window-shell/` | Marco de ventana de escritorio de nivel superior (usado solo en `login`). |
| `app-button/` | Botón único, con variantes `access` (diálogo), `menu` (opción de menú) y `plain` (link). |
| `menu-button-grid/` | Grilla/lista de `app-button` a partir de un arreglo de `MenuOption`, usada en todas las pantallas de menú. |
| `field-row/` | Fila horizontal de campos (`app-form-field` repetidos), usada en los formularios de compra. |
| `form-field/` | Un único campo (label + input/select) según su `FormFieldDefinition`. |
| `payment-panel/` | Panel "Formas de Pago" con métodos y total, repetido en los formularios de compra. |
| `section-divider/` | Barra divisoria con texto centrado (encabezados de sección tipo "LIQUIDACIÓN..."). |
| `purchase-form-view/` | Vista genérica de un formulario de compra completo: arma todas las secciones (arriba, identificación, federación, calidad, pesos, pago, liquidación) a partir de un `PurchaseFormContent`. La usan todas las pantallas de compra (seco, verde, pasilla, otros, futuro, cupos, consulta). |

### `features/` — pantallas

Cada pantalla "simple" es una carpeta con 2-3 archivos: `<nombre>.ts` (componente),
`<nombre>.html` (plantilla) y, si tiene forma propia de contenido, `<nombre>.model.ts`.
Las pantallas de formulario de compra viven agrupadas en `features/purchase-forms/`
porque todas reutilizan `purchase-form-view` + `PurchaseFormContent` y solo cambian
de JSON.

| Pantalla original (Access) | Carpeta | Ruta | JSON de contenido |
| --- | --- | --- | --- |
| AplicCompras Login | `features/login/` | `/login` | `login.json` + `shell.json` |
| Acceso Principal | `features/main-access/` | `/acceso-principal` | `main-access.json` |
| Menú Principal | `features/main-menu/` | `/menu-principal` | `main-menu.json` |
| Registro de Control | `features/control-record/` | `/registro-control` | `control-record.json` |
| Compras (menú) | `features/purchases-menu/` | `/compras` | `purchases-menu.json` |
| Compras Café Seco | `features/purchase-forms/dry-coffee/` | `/compras/cafe-seco` | `purchase-form-dry.json` |
| Compras Cafés Otros (COMPRASESP) | `features/purchase-forms/other-coffee/` | `/compras/cafe-otros` | `purchase-form-other.json` |
| Compras Café Verde (VERDES) | `features/purchase-forms/green-coffee/` | `/compras/cafe-verde` | `purchase-form-green.json` |
| Compra Pasilla | `features/purchase-forms/husk/` | `/compras/pasilla` | `purchase-form-husk.json` |
| Menú Compras a Futuro | `features/future-purchases-menu/` | `/compras/futuro` | `future-purchases-menu.json` |
| Asignación Cupos a Anuncios | `features/quota-assignment/` | `/compras/futuro/asignar-cupo` | `quota-assignment.json` |
| Ingresar Compras a Futuro (COMPRAS CUPOS) | `features/purchase-forms/future-purchase/` | `/compras/futuro/ingresar` | `purchase-form-future.json` |
| Facturar Anuncios con Cupos (ANUNCIADAS) | `features/purchase-forms/quota-billing/` | `/compras/futuro/facturar-cupos` | `purchase-form-quota-billing.json` |
| Menú Inventarios | `features/inventory-menu/` | `/compras/inventarios` | `inventory-menu.json` |
| Diálogo de rango de fechas | `features/date-range-dialog/` | `/compras/dialogo-fechas` | `date-range-dialog.json` |
| Consultar Compras | `features/purchase-query/` | `/compras/consulta` | `purchase-query.json` |

> Botones del menú "Compras a Futuro" que todavía no tienen pantalla propia
> (apuntan de vuelta a `/compras/futuro` como placeholder): *Restaurar Anuncios
> con Cupos*, *Facturar/Restaurar Compras Anunciadas*, *Facturar/Restaurar Cupos
> Fertifuturo*, *Generar Informes*.

## ¿Dónde está el JSON de cada pantalla?

En **`cafe-occidente-frontend/public/assets/data/`**. Hay un archivo por pantalla
(mismo nombre que usa `ContentService.loadJson('nombre')` en el componente). Ver
la tabla de arriba para el archivo exacto de cada una.

## ¿Dónde se manejan los colores?

En **`cafe-occidente-frontend/src/styles.css`**, dentro del bloque `@theme`. Ahí
están todos los tokens (`--color-panel-sky`, `--color-panel-teal-strong`,
`--color-btn-face`, `--color-accent-orange`, etc.) que Tailwind expone como
clases (`bg-panel-sky`, `border-btn-border`...). Ningún componente debería tener
un color hexadecimal escrito directamente; si falta un tono, se agrega ahí.
