import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { API } from './api';
import { Sesion } from './modelos';

const CLAVE = 'sigca.sesion';
/** Se renueva el token este tiempo antes de que expire. */
const ANTICIPO_RENOVACION_MS = 60_000;
/** Sin actividad del usuario durante este tiempo no se renueva y la sesión expira sola. */
const INACTIVIDAD_MAXIMA_MS = 15 * 60_000;

export interface LoginFuncionario {
  username: string;
  password: string;
  codigoDobleFactor?: string | null;
}

export interface LoginCiudadano {
  tipoIdentificacion: string;
  numeroIdentificacion: string;
  password: string;
}

/** Secreto TOTP que se registra en la aplicación autenticadora; el backend lo entrega una sola vez. */
export interface Enrolamiento {
  secreto: string;
  uriOtp: string;
}

export type TipoEnlace = 'activacion' | 'recuperacion';

/**
 * Sesión del usuario. El token vive en sessionStorage (se borra al cerrar la pestaña). Antes de expirar se
 * renueva (POST /auth/renovar) si hubo actividad reciente; el backend limita la duración total de la sesión.
 * La autorización real la hace el backend; aquí solo se usa para mostrar u ocultar opciones.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sesionActual = signal<Sesion | null>(this.leer());
  private temporizador?: ReturnType<typeof setTimeout>;
  private ultimaActividad = Date.now();

  readonly sesion = this.sesionActual.asReadonly();
  readonly autenticado = computed(() => this.sesionActual() !== null);
  readonly esCiudadano = computed(() => this.sesionActual()?.tipo === 'CIUDADANO');

  constructor() {
    if (typeof document !== 'undefined') {
      const marcar = () => this.registrarActividad();
      document.addEventListener('keydown', marcar, { passive: true });
      document.addEventListener('pointerdown', marcar, { passive: true });
      inject(DestroyRef).onDestroy(() => {
        document.removeEventListener('keydown', marcar);
        document.removeEventListener('pointerdown', marcar);
      });
    }
    this.programar();
  }

  loginFuncionario(datos: LoginFuncionario): Observable<Sesion> {
    return this.http.post<Sesion>(`${API}/auth/login`, datos).pipe(tap((s) => this.guardar(s)));
  }

  /** Genera el secreto del segundo factor; el siguiente login con un código válido lo activa. */
  enrolarDobleFactor(username: string, password: string): Observable<Enrolamiento> {
    return this.http.post<Enrolamiento>(`${API}/auth/doble-factor/enrolar`, { username, password });
  }

  loginCiudadano(datos: LoginCiudadano): Observable<Sesion> {
    return this.http.post<Sesion>(`${API}/auth/login-ciudadano`, datos).pipe(tap((s) => this.guardar(s)));
  }

  solicitarEnlace(tipo: TipoEnlace, tipoIdentificacion: string, numeroIdentificacion: string) {
    return this.http.post<void>(`${API}/auth/${tipo}/solicitar`, { tipoIdentificacion, numeroIdentificacion });
  }

  confirmarEnlace(tipo: TipoEnlace, token: string, password: string, confirmacion: string) {
    return this.http.post<void>(`${API}/auth/${tipo}/confirmar`, { token, password, confirmacion });
  }

  /** Devuelve una sesión nueva ya con permisos, así no hay que volver a ingresar tras la contraseña temporal. */
  cambiarPassword(actual: string, nueva: string, confirmacion: string): Observable<Sesion> {
    return this.http
      .post<Sesion>(`${API}/auth/cambiar-password`, { actual, nueva, confirmacion })
      .pipe(tap((s) => this.guardar(s)));
  }

  renovar(): Observable<Sesion> {
    return this.http.post<Sesion>(`${API}/auth/renovar`, {}).pipe(tap((s) => this.guardar(s)));
  }

  token(): string | null {
    return this.sesionActual()?.token ?? null;
  }

  tienePermiso(...permisos: string[]): boolean {
    const s = this.sesionActual();
    return !!s && permisos.some((p) => s.permisos.includes(p));
  }

  registrarActividad(): void {
    this.ultimaActividad = Date.now();
  }

  /** Cierra la sesión local y avisa al backend (auditoría CIERRE_SESION) si el token sigue vigente. */
  cerrarSesion(redirigir = true): void {
    const s = this.sesionActual();
    clearTimeout(this.temporizador);
    this.sesionActual.set(null);
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      /* almacenamiento no disponible */
    }
    if (s && new Date(s.expiraEn).getTime() > Date.now()) {
      this.http
        .post<void>(`${API}/auth/salir`, {}, { headers: { Authorization: `Bearer ${s.token}` } })
        .subscribe({ error: () => undefined });
    }
    if (redirigir) {
      this.router.navigate(['/ingresar']);
    }
  }

  private guardar(s: Sesion): void {
    this.sesionActual.set(s);
    this.registrarActividad();
    try {
      sessionStorage.setItem(CLAVE, JSON.stringify(s));
    } catch {
      /* almacenamiento no disponible: la sesión dura lo que la página */
    }
    this.programar();
  }

  private leer(): Sesion | null {
    try {
      const s = JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null') as Sesion | null;
      return s && new Date(s.expiraEn).getTime() > Date.now() ? s : null;
    } catch {
      return null;
    }
  }

  /**
   * Un minuto antes de expirar: renueva si el usuario estuvo activo, si no deja que la sesión venza. Si la
   * renovación falla (máximo de sesión alcanzado, cuenta desactivada) se cierra al expirar.
   */
  private programar(): void {
    clearTimeout(this.temporizador);
    const s = this.sesionActual();
    if (!s) {
      return;
    }
    const restante = new Date(s.expiraEn).getTime() - Date.now();
    const espera = restante - ANTICIPO_RENOVACION_MS;
    if (espera <= 0) {
      this.temporizador = setTimeout(() => this.cerrarSesion(), Math.max(0, restante));
      return;
    }
    this.temporizador = setTimeout(() => {
      if (Date.now() - this.ultimaActividad > INACTIVIDAD_MAXIMA_MS) {
        this.temporizador = setTimeout(() => this.cerrarSesion(), ANTICIPO_RENOVACION_MS);
        return;
      }
      this.renovar().subscribe({
        error: () => (this.temporizador = setTimeout(() => this.cerrarSesion(), Math.max(0, new Date(s.expiraEn).getTime() - Date.now()))),
      });
    }, Math.min(espera, 2_147_000_000));
  }
}
