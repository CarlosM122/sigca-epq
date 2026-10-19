import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Exige sesión; si el funcionario debe cambiar la contraseña, lo lleva a esa pantalla. */
export const autenticadoGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const s = auth.sesion();
  if (!s) {
    return router.createUrlTree(['/ingresar'], { queryParams: { volver: state.url } });
  }
  if (s.cambioPasswordRequerido && !state.url.startsWith('/cambiar-contrasena')) {
    return router.createUrlTree(['/cambiar-contrasena']);
  }
  return true;
};

/** Exige al menos uno de los permisos indicados en {@code data.permisos} de la ruta. */
export const permisoGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const permisos = (route.data['permisos'] as string[] | undefined) ?? [];
  return permisos.length === 0 || auth.tienePermiso(...permisos) ? true : inject(Router).createUrlTree(['/inicio']);
};

export const ciudadanoGuard: CanActivateFn = () =>
  inject(AuthService).esCiudadano() ? true : inject(Router).createUrlTree(['/inicio']);
