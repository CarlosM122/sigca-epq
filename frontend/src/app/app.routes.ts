import { Routes } from '@angular/router';

import { autenticadoGuard, ciudadanoGuard, permisoGuard } from './core/guardas';
import { Shell } from './layout/shell';

const p = (...permisos: string[]) => ({ canActivate: [permisoGuard], data: { permisos } });

export const routes: Routes = [
  { path: 'ingresar', loadComponent: () => import('./features/auth/ingresar').then((m) => m.Ingresar) },
  {
    path: 'cuenta/:tipo',
    loadComponent: () => import('./features/auth/enlace-cuenta').then((m) => m.EnlaceCuenta),
  },
  {
    path: 'pqr-publica',
    children: [
      { path: 'radicar', loadComponent: () => import('./features/publico/radicar-pqr').then((m) => m.RadicarPqr) },
      { path: 'consultar', loadComponent: () => import('./features/publico/consultar-pqr').then((m) => m.ConsultarPqr) },
    ],
  },
  { path: 'pago/resultado', loadComponent: () => import('./features/pagos/resultado-pago').then((m) => m.ResultadoPago) },
  {
    path: '',
    component: Shell,
    canActivate: [autenticadoGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      { path: 'inicio', loadComponent: () => import('./layout/inicio').then((m) => m.Inicio) },
      {
        path: 'cambiar-contrasena',
        loadComponent: () => import('./features/auth/cambiar-contrasena').then((m) => m.CambiarContrasena),
      },
      {
        path: 'portal',
        canActivate: [ciudadanoGuard],
        loadChildren: () => import('./features/portal/portal.routes').then((m) => m.PORTAL_ROUTES),
      },
      {
        path: 'dashboard',
        ...p('DASHBOARD_VER'),
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'usuarios',
        ...p('USUARIO_CONSULTAR'),
        loadChildren: () => import('./features/usuarios/usuarios.routes').then((m) => m.USUARIOS_ROUTES),
      },
      {
        path: 'contratos',
        ...p('CONTRATO_CONSULTAR', 'ORDEN_GESTIONAR'),
        loadChildren: () => import('./features/contratos/contratos.routes').then((m) => m.CONTRATOS_ROUTES),
      },
      {
        path: 'tarifas',
        ...p('TARIFA_GESTIONAR'),
        loadChildren: () => import('./features/tarifas/tarifas.routes').then((m) => m.TARIFAS_ROUTES),
      },
      {
        path: 'facturacion',
        ...p('FACTURA_CONSULTAR', 'FACTURA_GENERAR_LOTE', 'LECTURA_REGISTRAR', 'LECTURA_REVISAR', 'LECTURA_CARGAR_ARCHIVO'),
        loadChildren: () => import('./features/facturacion/facturacion.routes').then((m) => m.FACTURACION_ROUTES),
      },
      {
        path: 'pagos/registrar',
        ...p('PAGO_REGISTRAR_PRESENCIAL'),
        loadComponent: () => import('./features/pagos/registrar-pago').then((m) => m.RegistrarPago),
      },
      {
        path: 'pqr',
        ...p('PQR_CONSULTAR', 'PQR_CONSULTAR_TODAS'),
        loadChildren: () => import('./features/pqr/pqr.routes').then((m) => m.PQR_ROUTES),
      },
      {
        path: 'cartera',
        ...p('CARTERA_CONSULTAR'),
        loadChildren: () => import('./features/cartera/cartera.routes').then((m) => m.CARTERA_ROUTES),
      },
      {
        path: 'admin',
        ...p('FUNCIONARIO_GESTIONAR', 'ROL_GESTIONAR', 'PARAMETRO_GESTIONAR', 'AUDITORIA_CONSULTAR'),
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];
