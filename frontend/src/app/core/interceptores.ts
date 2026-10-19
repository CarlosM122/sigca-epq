import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { API } from './api';
import { AuthService } from './auth.service';
import { ApiError, esApiError } from './modelos';
import { AvisosService } from './avisos.service';

/** Rutas de autenticación cuyos errores muestra el propio formulario (no cierran sesión ni generan aviso). */
const RUTAS_LOGIN = ['/auth/login', '/auth/doble-factor/', '/auth/salir'];

/** Agrega el token solo a las llamadas de la propia API (nunca a terceros). */
export const tokenInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  if (token && req.url.startsWith(API) && !req.headers.has('Authorization')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

/**
 * Normaliza todo error HTTP a {@link ApiError}. Las validaciones (400 con campos) las muestra el
 * formulario; el resto se avisa aquí. Un 401 cierra la sesión.
 */
export const erroresInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const avisos = inject(AvisosService);
  return next(req).pipe(
    catchError((e: HttpErrorResponse) => {
      const error: ApiError = esApiError(e.error)
        ? e.error
        : {
            timestamp: new Date().toISOString(),
            status: e.status,
            codigo: e.status === 0 ? 'SIN_CONEXION' : 'ERROR_HTTP',
            mensaje: e.status === 0 ? 'No hay conexión con el servidor' : 'Ocurrió un error inesperado',
            ruta: req.url,
            errores: [],
          };
      const esLogin = RUTAS_LOGIN.some((r) => req.url.includes(r));
      if (e.status === 401 && !esLogin) {
        auth.cerrarSesion();
        avisos.error('Su sesión expiró. Ingrese de nuevo.');
      } else if (!(e.status === 400 && error.errores?.length) && !esLogin) {
        avisos.error(error.mensaje);
      }
      return throwError(() => error);
    }),
  );
};
