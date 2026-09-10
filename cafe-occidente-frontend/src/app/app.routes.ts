import { Routes } from '@angular/router';

/** Mapa de rutas de la aplicacion; cada pantalla migrada tiene su propia ruta. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'acceso-principal',
    loadComponent: () => import('./features/main-access/main-access').then((m) => m.MainAccessComponent),
  },
  {
    path: 'menu-principal',
    loadComponent: () => import('./features/main-menu/main-menu').then((m) => m.MainMenuComponent),
  },
  {
    path: 'registro-control',
    loadComponent: () =>
      import('./features/control-record/control-record').then((m) => m.ControlRecordComponent),
  },
  {
    path: 'usuarios',
    loadComponent: () =>
      import('./features/user-management/user-management').then((m) => m.UserManagementComponent),
  },
  {
    path: 'compras',
    loadComponent: () =>
      import('./features/purchases-menu/purchases-menu').then((m) => m.PurchasesMenuComponent),
  },
  {
    path: 'compras/cafe-seco',
    loadComponent: () =>
      import('./features/purchase-forms/dry-coffee/dry-coffee-form').then((m) => m.DryCoffeeFormComponent),
    canDeactivate: [
      (component: { canDeactivate(): boolean | Promise<boolean> }) => component.canDeactivate(),
    ],
  },
  {
    path: 'compras/cafe-otros',
    loadComponent: () =>
      import('./features/purchase-forms/other-coffee/other-coffee-form').then(
        (m) => m.OtherCoffeeFormComponent,
      ),
  },
  {
    path: 'compras/cafe-verde',
    loadComponent: () =>
      import('./features/purchase-forms/green-coffee/green-coffee-form').then(
        (m) => m.GreenCoffeeFormComponent,
      ),
  },
  {
    path: 'compras/pasilla',
    loadComponent: () =>
      import('./features/purchase-forms/husk/husk-form').then((m) => m.HuskFormComponent),
  },
  {
    path: 'compras/futuro',
    loadComponent: () =>
      import('./features/future-purchases-menu/future-purchases-menu').then(
        (m) => m.FuturePurchasesMenuComponent,
      ),
  },
  {
    path: 'compras/futuro/asignar-cupo',
    loadComponent: () =>
      import('./features/quota-assignment/quota-assignment').then(
        (m) => m.QuotaAssignmentComponent,
      ),
  },
  {
    path: 'compras/futuro/ingresar',
    loadComponent: () =>
      import('./features/purchase-forms/future-purchase/future-purchase-form').then(
        (m) => m.FuturePurchaseFormComponent,
      ),
  },
  {
    path: 'compras/futuro/facturar-cupos',
    loadComponent: () =>
      import('./features/purchase-forms/quota-billing/quota-billing-form').then(
        (m) => m.QuotaBillingFormComponent,
      ),
  },
  {
    path: 'compras/inventarios',
    loadComponent: () =>
      import('./features/inventory-menu/inventory-menu').then((m) => m.InventoryMenuComponent),
  },
  {
    path: 'compras/dialogo-fechas',
    loadComponent: () =>
      import('./features/date-range-dialog/date-range-dialog').then(
        (m) => m.DateRangeDialogComponent,
      ),
  },
  {
    path: 'compras/consulta',
    loadComponent: () =>
      import('./features/purchase-query/purchase-query').then((m) => m.PurchaseQueryComponent),
  },
  { path: '**', redirectTo: 'login' },
];
