import { Routes } from '@angular/router';

import { permisoGuard } from '../../core/guardas';

export const FACTURACION_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'facturas' },
  {
    path: 'lecturas',
    canActivate: [permisoGuard],
    data: { permisos: ['LECTURA_REGISTRAR', 'LECTURA_REVISAR', 'LECTURA_CARGAR_ARCHIVO'] },
    loadComponent: () => import('./lecturas').then((m) => m.Lecturas),
  },
  {
    path: 'facturas',
    canActivate: [permisoGuard],
    data: { permisos: ['FACTURA_CONSULTAR'] },
    loadComponent: () => import('./facturas').then((m) => m.Facturas),
  },
  {
    path: 'lotes',
    canActivate: [permisoGuard],
    data: { permisos: ['FACTURA_GENERAR_LOTE'] },
    loadComponent: () => import('./lotes').then((m) => m.Lotes),
  },
];
