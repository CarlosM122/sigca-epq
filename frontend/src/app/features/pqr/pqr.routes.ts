import { Routes } from '@angular/router';

import { permisoGuard } from '../../core/guardas';

export const PQR_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./bandeja').then((m) => m.Bandeja) },
  {
    path: 'presencial',
    canActivate: [permisoGuard],
    data: { permisos: ['PQR_REGISTRAR_PRESENCIAL'] },
    loadComponent: () => import('./radicar-presencial').then((m) => m.RadicarPresencial),
  },
  { path: ':id', loadComponent: () => import('./detalle-pqr').then((m) => m.DetallePqr) },
];
