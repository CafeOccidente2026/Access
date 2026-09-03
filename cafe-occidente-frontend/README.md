# CaféOccidente - Frontend de Compras (Angular 22)

Migración del frontend del sistema de escritorio **AplicCompras** (Access) a una
aplicación web con **Angular 22** y **Tailwind CSS v4**.

> Este entregable cubre únicamente el **diseño y la estructura** de las pantallas.
> La conexión con el backend (Java) y la lógica de negocio se implementarán en una
> siguiente etapa.

## Requisitos

- Node.js 22.22.3+ (o 24.15+ / 26+)
- npm 10+

## Instalación

```bash
npm install
```

## Ejecutar en desarrollo

```bash
npm start
```

## Compilar para producción

```bash
npm run build
```

## Arquitectura

```
src/app/
  core/            Modelos e infraestructura transversal (sin UI)
    models/        Interfaces TypeScript compartidas
    services/      ContentService (carga JSON) y NavigationService
  shared/ui/       Componentes de presentación reutilizables
  features/        Una carpeta por pantalla (una responsabilidad cada una)
public/assets/data/   Textos y datos de cada pantalla (JSON, editables sin tocar código)
public/assets/images/ Recursos gráficos (logo, etc.)
```

### Principios aplicados

- **Responsabilidad única**: cada clase/componente resuelve una sola cosa
  (carga de datos, navegación, o presentación de una pantalla puntual).
- **Sin datos quemados**: todos los textos, etiquetas y valores de ejemplo
  viven en `public/assets/data/*.json`, nunca escritos directamente en el
  código TypeScript o HTML.
- **Componentes compartidos**: la barra de ventana, los botones, los campos
  de formulario y el panel de "Formas de Pago" son componentes reutilizados
  por todas las pantallas que los necesitan, evitando duplicación.
- **Código en inglés**: nombres de clases, variables, métodos y selectores
  están en inglés (buenas prácticas); los comentarios puntuales y el texto
  visible al usuario están en español.

## Pantallas incluidas

| Pantalla original (Access)          | Ruta                     |
| ------------------------------------ | ------------------------ |
| AplicCompras Login                   | `/login`                 |
| Acceso Principal                     | `/acceso-principal`      |
| Menú Principal                       | `/menu-principal`        |
| Registro de Control                  | `/registro-control`      |
| Compras (menú)                       | `/compras`                |
| Compras Café Seco                    | `/compras/cafe-seco`     |
| Compras Cafés Otros (COMPRASESP)     | `/compras/cafe-otros`    |
| Compras Café Verde (VERDES)          | `/compras/cafe-verde`    |
| Compra Pasilla                       | `/compras/pasilla`       |
| Menú Compras a Futuro                | `/compras/futuro`        |
| Menú Inventarios                     | `/compras/inventarios`   |
| Diálogo de rango de fechas           | `/compras/dialogo-fechas`|
| Consultar Compras                    | `/compras/consulta`      |
