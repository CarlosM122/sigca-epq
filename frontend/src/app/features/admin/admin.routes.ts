import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { permisoGuard } from '../../core/guardas';
import { AdminLayout, SECCIONES_ADMIN } from './admin-layout';

const p = (...permisos: string[]) => ({ canActivate: [permisoGuard], data: { permisos } });

/** Lleva /admin a la primera sección que el usuario puede ver. */
const primeraSeccion = () => {
  const auth = inject(AuthService);
  const s = SECCIONES_ADMIN.find((x) => auth.tienePermiso(...x.permisos));
  return inject(Router).createUrlTree(s ? ['/admin', s.ruta] : ['/inicio']);
};

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayout,
    children: [
      { path: '', pathMatch: 'full', canActivate: [primeraSeccion], children: [] },
      {
        path: 'funcionarios',
        ...p('FUNCIONARIO_GESTIONAR'),
        loadComponent: () => import('./funcionarios').then((m) => m.Funcionarios),
      },
      { path: 'roles', ...p('ROL_GESTIONAR'), loadComponent: () => import('./matriz-permisos').then((m) => m.MatrizPermisos) },
      { path: 'parametros', ...p('PARAMETRO_GESTIONAR'), loadComponent: () => import('./parametros').then((m) => m.Parametros) },
      { path: 'festivos', ...p('PARAMETRO_GESTIONAR'), loadComponent: () => import('./festivos').then((m) => m.Festivos) },
      { path: 'municipios', ...p('PARAMETRO_GESTIONAR'), loadComponent: () => import('./municipios').then((m) => m.Municipios) },
      { path: 'auditoria', ...p('AUDITORIA_CONSULTAR'), loadComponent: () => import('./auditoria').then((m) => m.Auditoria) },
    ],
  },
];
