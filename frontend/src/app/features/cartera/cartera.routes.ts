import { Routes } from '@angular/router';

import { permisoGuard } from '../../core/guardas';

export const CARTERA_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./morosos').then((m) => m.Morosos) },
  { path: 'acuerdos', loadComponent: () => import('./acuerdos').then((m) => m.Acuerdos) },
  { path: 'acuerdos/:id', loadComponent: () => import('./detalle-acuerdo').then((m) => m.DetalleAcuerdo) },
  {
    path: 'acuerdo/:usuarioId',
    canActivate: [permisoGuard],
    data: { permisos: ['ACUERDO_GESTIONAR'] },
    loadComponent: () => import('./nuevo-acuerdo').then((m) => m.NuevoAcuerdo),
  },
];
