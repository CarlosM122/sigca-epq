import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { ApiError, Sesion } from '../../core/modelos';
import { aplicarErrorPassword } from './errores-password';

function error(codigo: string): ApiError {
  return { timestamp: '', status: 422, codigo, mensaje: `mensaje ${codigo}`, ruta: '', errores: [] };
}

function sesion(minutos: number, cambio = false): Sesion {
  return {
    token: 't' + minutos,
    expiraEn: new Date(Date.now() + minutos * 60_000).toISOString(),
    tipo: 'FUNCIONARIO',
    nombre: 'Prueba',
    roles: ['ASESOR'],
    permisos: cambio ? [] : ['USUARIO_CONSULTAR'],
    cambioPasswordRequerido: cambio,
  };
}

describe('errores de contraseña', () => {
  it('ubica cada código del backend en su campo', () => {
    const form = new FormGroup({ actual: new FormControl(''), nueva: new FormControl(''), confirmacion: new FormControl('') });
    expect(aplicarErrorPassword(form, error('PASSWORD_ACTUAL_INCORRECTA'))).toBe(true);
    expect(form.controls.actual.errors?.['servidor']).toBe('mensaje PASSWORD_ACTUAL_INCORRECTA');
    aplicarErrorPassword(form, error('PASSWORD_NO_COINCIDE'));
    expect(form.controls.confirmacion.hasError('servidor')).toBe(true);
    expect(aplicarErrorPassword(form, error('OTRO'))).toBe(false);
  });

  it('traduce el nombre del campo de la nueva contraseña', () => {
    const form = new FormGroup({ password: new FormControl(''), confirmacion: new FormControl('') });
    expect(aplicarErrorPassword(form, error('PASSWORD_ES_IDENTIFICACION'), { nueva: 'password' })).toBe(true);
    expect(form.controls.password.hasError('servidor')).toBe(true);
  });
});

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'ingresar', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('el cambio de contraseña reemplaza la sesión temporal por una con permisos', () => {
    auth.loginFuncionario({ username: 'a', password: 'b', codigoDobleFactor: '123456' }).subscribe();
    http.expectOne('/api/auth/login').flush(sesion(15, true));
    expect(auth.tienePermiso('USUARIO_CONSULTAR')).toBe(false);

    auth.cambiarPassword('Vieja.2026x', 'Nueva.2026x', 'Nueva.2026x').subscribe();
    http.expectOne('/api/auth/cambiar-password').flush(sesion(15));
    expect(auth.sesion()?.cambioPasswordRequerido).toBe(false);
    expect(auth.tienePermiso('USUARIO_CONSULTAR')).toBe(true);
  });

  it('renueva el token un minuto antes de expirar si hubo actividad', () => {
    auth.loginFuncionario({ username: 'a', password: 'b' }).subscribe();
    http.expectOne('/api/auth/login').flush(sesion(15));
    vi.advanceTimersByTime(13 * 60_000);
    http.expectNone('/api/auth/renovar');
    auth.registrarActividad();
    vi.advanceTimersByTime(60_000 + 10);
    const req = http.expectOne('/api/auth/renovar');
    req.flush(sesion(15));
    expect(auth.token()).toBe('t15');
  });

  it('sin actividad no renueva y cierra la sesión al expirar avisando al backend', () => {
    auth.loginFuncionario({ username: 'a', password: 'b' }).subscribe();
    http.expectOne('/api/auth/login').flush(sesion(15));
    vi.setSystemTime(Date.now() + 14 * 60_000 + 1);
    vi.advanceTimersByTime(14 * 60_000 + 1);
    http.expectNone('/api/auth/renovar');
    vi.advanceTimersByTime(60_000);
    expect(auth.autenticado()).toBe(false);
  });

  it('al cerrar sesión registra la salida con el token vigente', () => {
    auth.loginFuncionario({ username: 'a', password: 'b' }).subscribe();
    http.expectOne('/api/auth/login').flush(sesion(15));
    auth.cerrarSesion(false);
    const req = http.expectOne('/api/auth/salir');
    expect(req.request.headers.get('Authorization')).toBe('Bearer t15');
    req.flush(null);
    expect(auth.autenticado()).toBe(false);
  });
});
