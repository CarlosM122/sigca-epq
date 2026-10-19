import { Routes } from '@angular/router';

import { permisoGuard } from '../../core/guardas';

export const USUARIOS_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./lista-usuarios').then((m) => m.ListaUsuarios) },
  {
    path: 'nuevo',
    canActivate: [permisoGuard],
    data: { permisos: ['USUARIO_CREAR'] },
    loadComponent: () => import('./formulario-usuario').then((m) => m.FormularioUsuario),
  },
  { path: ':id', loadComponent: () => import('./detalle-usuario').then((m) => m.DetalleUsuario) },
  {
    path: ':id/editar',
    canActivate: [permisoGuard],
    data: { permisos: ['USUARIO_MODIFICAR'] },
    loadComponent: () => import('./formulario-usuario').then((m) => m.FormularioUsuario),
  },
];
