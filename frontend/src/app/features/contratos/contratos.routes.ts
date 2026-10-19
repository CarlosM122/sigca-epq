import { Routes } from '@angular/router';

import { permisoGuard } from '../../core/guardas';

/** Contratos, predios con sus medidores y bandeja de órdenes de suspensión y reconexión. */
export const CONTRATOS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permisoGuard],
    data: { permisos: ['CONTRATO_CONSULTAR'] },
    loadComponent: () => import('./lista-contratos').then((m) => m.ListaContratos),
  },
  {
    path: 'ordenes',
    canActivate: [permisoGuard],
    data: { permisos: ['ORDEN_GESTIONAR', 'CONTRATO_CONSULTAR'] },
    loadComponent: () => import('./bandeja-ordenes').then((m) => m.BandejaOrdenes),
  },
  {
    path: 'predios/:id',
    canActivate: [permisoGuard],
    data: { permisos: ['PREDIO_GESTIONAR', 'CONTRATO_CONSULTAR', 'USUARIO_CONSULTAR'] },
    loadComponent: () => import('./detalle-predio').then((m) => m.DetallePredio),
  },
  {
    path: ':id',
    canActivate: [permisoGuard],
    data: { permisos: ['CONTRATO_CONSULTAR'] },
    loadComponent: () => import('./detalle-contrato').then((m) => m.DetalleContrato),
  },
];
