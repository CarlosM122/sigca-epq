import { Routes } from '@angular/router';

export const PORTAL_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'facturas' },
  { path: 'facturas', loadComponent: () => import('./mis-facturas').then((m) => m.MisFacturas) },
  { path: 'contratos', loadComponent: () => import('./mis-contratos').then((m) => m.MisContratos) },
  { path: 'pqr', loadComponent: () => import('./mis-pqr').then((m) => m.MisPqr) },
];
