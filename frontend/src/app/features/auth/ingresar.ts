import { Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService, Enrolamiento } from '../../core/auth.service';
import { ApiError, Sesion, TIPOS_IDENTIFICACION } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { Icono } from '../../shared/icono';
import { Logo } from '../../shared/logo';
import { listoParaEnviar } from '../../shared/formularios';
import { documentoValido } from '../../shared/validadores';

@Component({
  selector: 'app-ingresar',
  imports: [ReactiveFormsModule, RouterLink, CampoError, Icono, Logo],
  template: `
    <div class="pagina-ingreso">
      <aside class="ingreso-marca">
        <app-logo [claro]="true" [tamano]="42" />
        <div class="ingreso-mensaje">
          <h2>Sus servicios públicos, en línea y en un solo lugar</h2>
          <p>Acueducto, alcantarillado y gas de Empresas Públicas del Quindío.</p>
          <ul>
            <li><span><app-icono nombre="receipt" [tamano]="18" /></span>Consulte, descargue y pague sus facturas</li>
            <li><span><app-icono nombre="message-square-text" [tamano]="18" /></span>Radique PQR y siga su respuesta</li>
            <li><span><app-icono nombre="shield-check" [tamano]="18" /></span>Acceso protegido con segundo factor para funcionarios</li>
          </ul>
        </div>
        <p class="ingreso-pie">Empresas Públicas del Quindío E.S.P.</p>
        <svg class="ingreso-ondas" viewBox="0 0 400 200" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 110 C 90 80, 170 140, 250 110 S 360 80, 400 100 L400 200 L0 200 Z" fill="rgba(255,255,255,.06)" />
          <path d="M0 145 C 80 120, 180 175, 270 145 S 370 125, 400 140 L400 200 L0 200 Z" fill="rgba(255,255,255,.08)" />
        </svg>
      </aside>

      <main class="ingreso-formulario">
        <div class="ingreso-caja">
          <app-logo class="ingreso-logo-movil" />
          <h1>Ingresar a SIGCA</h1>
          <p class="ayuda">Bienvenido. Elija cómo desea ingresar.</p>
          <div class="pestanas" role="tablist">
            <button type="button" role="tab" [class.activa]="modo() === 'ciudadano'" [attr.aria-selected]="modo() === 'ciudadano'" (click)="modo.set('ciudadano')">
              <app-icono nombre="user" [tamano]="17" />Soy usuario
            </button>
            <button type="button" role="tab" [class.activa]="modo() === 'funcionario'" [attr.aria-selected]="modo() === 'funcionario'" (click)="modo.set('funcionario')">
              <app-icono nombre="building-2" [tamano]="17" />Soy funcionario
            </button>
          </div>

          @if (modo() === 'funcionario') {
            <form [formGroup]="funcionario" (ngSubmit)="ingresarFuncionario()" novalidate>
              <label>Usuario
                <input formControlName="username" autocomplete="username" maxlength="60" placeholder="nombre.apellido" />
              </label>
              <app-campo-error [control]="funcionario.controls.username" />
              <label>Contraseña
                <span class="campo-clave">
                  <input [type]="verClave() ? 'text' : 'password'" formControlName="password" autocomplete="current-password" maxlength="64" />
                  <button type="button" class="ver-clave" (click)="verClave.set(!verClave())" [attr.aria-label]="verClave() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                    <app-icono [nombre]="verClave() ? 'eye-off' : 'eye'" [tamano]="18" />
                  </button>
                </span>
              </label>
              <app-campo-error [control]="funcionario.controls.password" />
              @if (enrolamiento(); as e) {
                <div class="enrolamiento" role="status">
                  <p class="enrolamiento-titulo"><app-icono nombre="smartphone" [tamano]="20" />Configure su segundo factor</p>
                  <ol>
                    <li>Abra su aplicación autenticadora (Google Authenticator, Microsoft Authenticator u otra compatible con TOTP).</li>
                    <li>Agregue una cuenta con esta clave de configuración:
                      <code class="clave-otp">{{ claveLegible(e.secreto) }}</code>
                      <span class="ayuda">En el celular también puede <a [href]="enlaceOtp()">abrir el enlace de configuración</a>.</span>
                    </li>
                    <li>Escriba abajo el código de 6 dígitos que muestra la aplicación.</li>
                  </ol>
                  <p class="ayuda">La clave no se volverá a mostrar: si la pierde antes de terminar, ingrese de nuevo para generar otra.</p>
                </div>
              }
              @if (pideCodigo()) {
                <label>Código de verificación de 6 dígitos (app autenticadora)
                  <input class="codigo-otp" formControlName="codigoDobleFactor" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="000000" />
                </label>
                <app-campo-error [control]="funcionario.controls.codigoDobleFactor" />
              }
              @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
              <button type="submit" class="bloque" [disabled]="enviando()">
                @if (enviando()) { <span class="girando"></span> } Ingresar
              </button>
            </form>
          } @else {
            <form [formGroup]="ciudadano" (ngSubmit)="ingresarCiudadano()" novalidate>
              <label>Tipo de identificación
                <select formControlName="tipoIdentificacion">
                  @for (t of tipos; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
                </select>
              </label>
              <label>Número de identificación
                <input formControlName="numeroIdentificacion" autocomplete="username" maxlength="15" inputmode="text" />
              </label>
              <app-campo-error [control]="ciudadano.controls.numeroIdentificacion" />
              <label>Contraseña
                <span class="campo-clave">
                  <input [type]="verClave() ? 'text' : 'password'" formControlName="password" autocomplete="current-password" maxlength="64" />
                  <button type="button" class="ver-clave" (click)="verClave.set(!verClave())" [attr.aria-label]="verClave() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                    <app-icono [nombre]="verClave() ? 'eye-off' : 'eye'" [tamano]="18" />
                  </button>
                </span>
              </label>
              <app-campo-error [control]="ciudadano.controls.password" />
              @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
              <button type="submit" class="bloque" [disabled]="enviando()">
                @if (enviando()) { <span class="girando"></span> } Ingresar
              </button>
            </form>
            <p class="ingreso-enlaces">
              <span>¿Primera vez? <a routerLink="/cuenta/activacion">Active su cuenta</a></span>
              <a routerLink="/cuenta/recuperacion">Olvidé mi contraseña</a>
            </p>
          }

          <div class="sin-ingresar">
            <p>Sin ingresar puede <a routerLink="/pqr-publica/radicar">radicar una PQR</a> o
              <a routerLink="/pqr-publica/consultar">consultar su radicado</a>.</p>
          </div>
        </div>
      </main>
    </div>
  `,
})
export class Ingresar {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);

  readonly tipos = TIPOS_IDENTIFICACION;
  readonly modo = signal<'ciudadano' | 'funcionario'>('ciudadano');
  readonly pideCodigo = signal(false);
  readonly enrolamiento = signal<Enrolamiento | null>(null);
  /** Angular marca como inseguro el esquema otpauth://; solo se confía en el URI que entrega el backend. */
  readonly enlaceOtp = computed<SafeUrl | null>(() => {
    const uri = this.enrolamiento()?.uriOtp;
    return uri?.startsWith('otpauth://totp/') ? this.sanitizer.bypassSecurityTrustUrl(uri) : null;
  });
  readonly verClave = signal(false);
  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  readonly funcionario = this.fb.group({
    username: ['', [Validators.required, Validators.pattern(/^[a-z0-9._-]{3,60}$/)]],
    password: ['', [Validators.required, Validators.maxLength(64)]],
    codigoDobleFactor: ['', [Validators.pattern(/^\d{6}$/)]],
  });

  readonly ciudadano = this.fb.group(
    {
      tipoIdentificacion: ['CC', Validators.required],
      numeroIdentificacion: ['', Validators.required],
      password: ['', [Validators.required, Validators.maxLength(64)]],
    },
    { validators: documentoValido() },
  );

  constructor() {
    // Si cambian usuario o contraseña, el enrolamiento y el código anteriores dejan de aplicar
    for (const c of [this.funcionario.controls.username, this.funcionario.controls.password]) {
      c.valueChanges.subscribe(() => {
        if (this.pideCodigo() && !this.enviando()) {
          this.reiniciarDobleFactor();
        }
      });
    }
  }

  /** Clave Base32 en grupos de 4 para copiarla a mano sin errores. */
  claveLegible(secreto: string): string {
    return secreto.replace(/(.{4})/g, '$1 ').trim();
  }

  ingresarFuncionario() {
    if (!listoParaEnviar(this.funcionario)) return;
    const v = this.funcionario.getRawValue();
    this.funcionario.controls.username.setValue(v.username.trim().toLowerCase(), { emitEvent: false });
    this.enviar(
      this.auth.loginFuncionario({
        username: v.username.trim().toLowerCase(),
        password: v.password,
        codigoDobleFactor: v.codigoDobleFactor || null,
      }),
    );
  }

  ingresarCiudadano() {
    if (!listoParaEnviar(this.ciudadano)) return;
    const v = this.ciudadano.getRawValue();
    this.enviar(this.auth.loginCiudadano({ ...v, numeroIdentificacion: v.numeroIdentificacion.trim().toUpperCase() }));
  }

  private enviar(peticion: Observable<Sesion>) {
    this.enviando.set(true);
    this.error.set(null);
    peticion.subscribe({
      next: (s: Sesion) => {
        const volver = this.route.snapshot.queryParamMap.get('volver');
        // Solo rutas internas: evita redirecciones abiertas.
        const destino = s.cambioPasswordRequerido ? '/cambiar-contrasena' : volver?.startsWith('/') && !volver.startsWith('//') ? volver : '/inicio';
        this.router.navigateByUrl(destino);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        switch (e.codigo) {
          case 'DOBLE_FACTOR_ENROLAR':
            this.enrolar();
            return;
          case 'DOBLE_FACTOR_REQUERIDO':
            this.pedirCodigo();
            break;
          case 'DOBLE_FACTOR_INVALIDO':
            this.pedirCodigo();
            this.funcionario.controls.codigoDobleFactor.setValue('', { emitEvent: false });
            break;
        }
        this.error.set(e.mensaje);
      },
    });
  }

  /** Primer ingreso: se genera el secreto, se muestra para la app y se pide el primer código, que lo activa. */
  private enrolar() {
    const v = this.funcionario.getRawValue();
    this.enviando.set(true);
    this.auth.enrolarDobleFactor(v.username.trim().toLowerCase(), v.password).subscribe({
      next: (e) => {
        this.enviando.set(false);
        this.enrolamiento.set(e);
        this.pedirCodigo();
        this.error.set(null);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        this.error.set(e.mensaje);
      },
    });
  }

  private pedirCodigo() {
    this.pideCodigo.set(true);
    const c = this.funcionario.controls.codigoDobleFactor;
    c.setValidators([Validators.required, Validators.pattern(/^\d{6}$/)]);
    c.updateValueAndValidity({ emitEvent: false });
  }

  private reiniciarDobleFactor() {
    this.pideCodigo.set(false);
    this.enrolamiento.set(null);
    const c = this.funcionario.controls.codigoDobleFactor;
    c.setValidators([Validators.pattern(/^\d{6}$/)]);
    c.setValue('', { emitEvent: false });
    c.updateValueAndValidity({ emitEvent: false });
  }
}
