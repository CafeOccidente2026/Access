import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

/** Mapa de rutas de la aplicacion; cada pantalla migrada tiene su propia ruta.
 *  Todas requieren sesion (authGuard) salvo /login. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'acceso-principal',
    canActivate: [authGuard],
    loadComponent: () => import('./features/main-access/main-access').then((m) => m.MainAccessComponent),
  },
  {
    path: 'usuarios',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () =>
      import('./features/user-management/user-management').then((m) => m.UserManagementComponent),
  },
  {
    path: 'menu-principal',
    canActivate: [authGuard],
    loadComponent: () => import('./features/main-menu/main-menu').then((m) => m.MainMenuComponent),
  },
  {
    path: 'registro-control',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/control-record/control-record').then((m) => m.ControlRecordComponent),
  },
  {
    path: 'compras',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchases-menu/purchases-menu').then((m) => m.PurchasesMenuComponent),
  },
  {
    path: 'compras/cafe-seco',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchase-forms/dry-coffee/dry-coffee-form').then((m) => m.DryCoffeeFormComponent),
  },
  {
    path: 'compras/cafe-otros',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchase-forms/other-coffee/other-coffee-form').then(
        (m) => m.OtherCoffeeFormComponent,
      ),
  },
  {
    path: 'compras/cafe-verde',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchase-forms/green-coffee/green-coffee-form').then(
        (m) => m.GreenCoffeeFormComponent,
      ),
  },
  {
    path: 'compras/pasilla',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchase-forms/husk/husk-form').then((m) => m.HuskFormComponent),
  },
  {
    path: 'compras/futuro',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/future-purchases-menu/future-purchases-menu').then(
        (m) => m.FuturePurchasesMenuComponent,
      ),
  },
  {
    path: 'compras/futuro/asignar-cupo',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/quota-assignment/quota-assignment').then(
        (m) => m.QuotaAssignmentComponent,
      ),
  },
  {
    path: 'compras/futuro/ingresar',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchase-forms/future-purchase/future-purchase-form').then(
        (m) => m.FuturePurchaseFormComponent,
      ),
  },
  {
    path: 'compras/futuro/facturar-cupos',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchase-forms/quota-billing/quota-billing-form').then(
        (m) => m.QuotaBillingFormComponent,
      ),
  },
  {
    path: 'compras/inventarios',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/inventory-menu/inventory-menu').then((m) => m.InventoryMenuComponent),
  },
  {
    path: 'compras/dialogo-fechas',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/date-range-dialog/date-range-dialog').then(
        (m) => m.DateRangeDialogComponent,
      ),
  },
  {
    path: 'compras/consulta',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/purchase-query/purchase-query').then((m) => m.PurchaseQueryComponent),
  },
  { path: '**', redirectTo: 'login' },
];
