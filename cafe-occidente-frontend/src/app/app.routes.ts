import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { UserRole } from './core/models';
import { dryCoffeeUnsavedChangesGuard } from './features/purchase-forms/dry-coffee/unsaved-changes.guard';

/** "MENUS SUMINISTROS": los menus, altas e informes comparten componente; data elige el JSON. */
function suppliesRoutes(): Routes {
  const menu = () => import('./features/supplies-menu/supplies-menu').then((m) => m.SuppliesMenuComponent);
  const entry = () => import('./features/supplies-entry/supplies-entry').then((m) => m.SuppliesEntryComponent);
  const report = () => import('./features/supplies-report/supplies-report').then((m) => m.SuppliesReportComponent);
  const entries: Record<string, string> = {
    caja: 'cash',
    'ajustes-caja': 'adjustment',
    ingresar: 'supply',
    'cheques-girados': 'issuedCheck',
    empaques: 'packaging',
    'prestamo-empaques': 'packagingLoan',
    'caja-menor/ingresar': 'pettyCash',
    'caja-menor/gastos': 'pettyCashExpense',
  };
  const reports: Record<string, string> = {
    'informe-caja': 'cash',
    'informe-rp': 'suppliesRp',
    'informe-lf': 'suppliesLf',
    'informe-empaques': 'packaging',
    'relacion-cheques': 'checks',
    'relacion-cheques-especial': 'checksSpecial',
    'formas-de-pago': 'paymentMethods',
    'caja-menor/informe': 'pettyCash',
  };
  return [
    { path: 'suministros', loadComponent: menu, data: { content: 'supplies-menu' } },
    { path: 'suministros/caja-menor', loadComponent: menu, data: { content: 'petty-cash-menu' } },
    ...Object.entries(entries).map(([path, screen]) => ({ path: `suministros/${path}`, loadComponent: entry, data: { screen } })),
    ...Object.entries(reports).map(([path, key]) => ({ path: `suministros/${path}`, loadComponent: report, data: { report: key } })),
  ];
}

/** "MENUS VENDEDORES": el menu reutiliza el panel de Suministros (columnas + Volver). */
function vendorsRoutes(): Routes {
  const report = () => import('./features/beneficiary-report/beneficiary-report').then((m) => m.BeneficiaryReportComponent);
  const ness = () => import('./features/ness-quotas/ness-quotas').then((m) => m.NessQuotasComponent);
  return [
    {
      path: 'vendedores',
      loadComponent: () => import('./features/supplies-menu/supplies-menu').then((m) => m.SuppliesMenuComponent),
      data: { content: 'vendors-menu' },
    },
    { path: 'vendedores/ingresar', loadComponent: () => import('./features/vendor-entry/vendor-entry').then((m) => m.VendorEntryComponent) },
    { path: 'vendedores/beneficiario', loadComponent: report, data: { report: 'beneficiary' } },
    { path: 'vendedores/beneficiario-fechas', loadComponent: report, data: { report: 'summary' } },
    { path: 'vendedores/asociados', loadComponent: () => import('./features/associates/associates').then((m) => m.AssociatesComponent) },
    { path: 'vendedores/cupos-ness', loadComponent: ness, data: { view: 'quotas' } },
    { path: 'vendedores/saldos-cupos-ness', loadComponent: ness, data: { view: 'balances' } },
  ];
}

const ADMIN: readonly UserRole[] = ['ADMINISTRADOR'];
const ADMIN_USER: readonly UserRole[] = ['ADMINISTRADOR', 'USUARIO'];
const ADMIN_OPS: readonly UserRole[] = ['ADMINISTRADOR', 'OPERACIONES'];
const ALL: readonly UserRole[] = ['ADMINISTRADOR', 'USUARIO', 'OPERACIONES'];

/** Mapa de rutas de la aplicacion; cada pantalla migrada tiene su propia ruta. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    // Toda pantalla salvo el login: sesion obligatoria y data.roles (por defecto Administrador y Usuario).
    path: '',
    canActivateChild: [authGuard],
    children: [
      {
        path: 'acceso-principal',
        data: { roles: ALL },
        loadComponent: () => import('./features/main-access/main-access').then((m) => m.MainAccessComponent),
      },
      {
        path: 'menu-principal',
        data: { roles: ALL },
        loadComponent: () => import('./features/main-menu/main-menu').then((m) => m.MainMenuComponent),
      },
      {
        path: 'registro-control',
        data: { roles: ADMIN, screens: ['REGISTRO_CONTROL'] },
        loadComponent: () =>
          import('./features/control-record/control-record').then((m) => m.ControlRecordComponent),
      },
      {
        path: 'usuarios',
        data: { roles: ADMIN },
        loadComponent: () =>
          import('./features/user-management/user-management').then((m) => m.UserManagementComponent),
      },
      {
        path: 'permisos',
        data: { roles: ADMIN },
        loadComponent: () => import('./features/permissions/permissions').then((m) => m.PermissionsComponent),
      },
      {
        path: 'historial',
        data: { roles: ADMIN, screens: ['HISTORIAL'] },
        loadComponent: () => import('./features/history/history').then((m) => m.HistoryComponent),
      },
      {
        path: 'anuncios',
        data: { roles: ADMIN_OPS, screens: ['ACTUALIZAR_ANUNCIOS'] },
        loadComponent: () =>
          import('./features/announcements-menu/announcements-menu').then(
            (m) => m.AnnouncementsMenuComponent,
          ),
      },
      {
        path: 'anuncios/actualizar',
        data: { roles: ADMIN_OPS, screens: ['ACTUALIZAR_ANUNCIOS'] },
        loadComponent: () =>
          import('./features/announcement-update/announcement-update').then(
            (m) => m.AnnouncementUpdateComponent,
          ),
      },
      {
        path: 'anuncios/actualizar-pasilla',
        data: { roles: ADMIN_OPS, screens: ['ACTUALIZAR_ANUNCIOS'] },
        loadComponent: () =>
          import('./features/announcement-update-husk/announcement-update-husk').then(
            (m) => m.AnnouncementUpdateHuskComponent,
          ),
      },
      {
        path: 'compras',
        // Quien solo tiene Anular Documento concedido entra al menu y ve solo esa opcion (RF-02).
        data: { screens: ['ANULAR_DOCUMENTO'] },
        loadComponent: () =>
          import('./features/purchases-menu/purchases-menu').then((m) => m.PurchasesMenuComponent),
      },
      {
        path: 'compras/cafe-seco',
        loadComponent: () =>
          import('./features/purchase-forms/dry-coffee/dry-coffee-form').then((m) => m.DryCoffeeFormComponent),
        canDeactivate: [dryCoffeeUnsavedChangesGuard],
      },
      {
        path: 'compras/cafe-verde',
        loadComponent: () =>
          import('./features/purchase-forms/green-coffee/green-coffee-form').then(
            (m) => m.GreenCoffeeFormComponent,
          ),
        canDeactivate: [dryCoffeeUnsavedChangesGuard],
      },
      {
        path: 'compras/pasilla',
        loadComponent: () =>
          import('./features/purchase-forms/husk/husk-form').then((m) => m.HuskFormComponent),
        canDeactivate: [dryCoffeeUnsavedChangesGuard],
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
        data: { roles: ADMIN_USER },
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
        canDeactivate: [dryCoffeeUnsavedChangesGuard],
      },
      {
        path: 'compras/futuro/facturar-cupos',
        loadComponent: () =>
          import('./features/purchase-forms/quota-purchase/quota-purchase-form').then(
            (m) => m.QuotaPurchaseFormComponent,
          ),
      },
      {
        path: 'compras/futuro/facturar-anunciadas',
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
        path: 'compras/inventarios/consulta',
        loadComponent: () =>
          import('./features/inventory-consulta/inventory-consulta').then((m) => m.InventoryConsultaComponent),
      },
      {
        path: 'compras/inventarios/salidas',
        loadComponent: () =>
          import('./features/remission-form/remission-form').then((m) => m.RemissionFormComponent),
      },
      {
        path: 'compras/inventarios/conductores',
        loadComponent: () =>
          import('./features/conductor-form/conductor-form').then((m) => m.ConductorFormComponent),
      },
      {
        path: 'compras/inventarios/reporte-cod',
        loadComponent: () =>
          import('./features/inventory-report/inventory-report').then((m) => m.InventoryReportComponent),
        data: { mode: 'code' },
      },
      {
        path: 'compras/inventarios/reporte-esp',
        loadComponent: () =>
          import('./features/inventory-report/inventory-report').then((m) => m.InventoryReportComponent),
        data: { mode: 'special' },
      },
      {
        path: 'compras/inventarios/reimprimir-remision',
        loadComponent: () =>
          import('./features/remission-reprint/remission-reprint').then((m) => m.RemissionReprintComponent),
      },
      {
        path: 'compras/remesa',
        loadComponent: () =>
          import('./features/remesa-export/remesa-export').then((m) => m.RemesaExportComponent),
      },
      {
        path: 'compras/reporte-anuncio',
        data: { roles: ADMIN_USER },
        loadComponent: () =>
          import('./features/announcement-report/announcement-report').then((m) => m.AnnouncementReportComponent),
      },
      {
        path: 'compras/dialogo-fechas',
        loadComponent: () =>
          import('./features/date-range-dialog/date-range-dialog').then(
            (m) => m.DateRangeDialogComponent,
          ),
      },
      {
        path: 'compras/anular',
        data: { roles: ADMIN, screens: ['ANULAR_DOCUMENTO'] },
        loadComponent: () =>
          import('./features/purchase-annulment/purchase-annulment').then((m) => m.PurchaseAnnulmentComponent),
      },
      {
        path: 'compras/consulta',
        loadComponent: () =>
          import('./features/purchase-query/purchase-query').then((m) => m.PurchaseQueryComponent),
      },
      ...suppliesRoutes(),
      ...vendorsRoutes(),
    ],
  },
  { path: '**', redirectTo: 'login' },
];
