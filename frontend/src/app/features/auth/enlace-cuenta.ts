import { Component, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService, TipoEnlace } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError, TIPOS_IDENTIFICACION } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { coinciden, documentoValido, passwordSegura } from '../../shared/validadores';
import { aplicarErrorPassword } from './errores-password';
import { MarcoPublico } from '../../shared/marco-publico';
import { Icono } from '../../shared/icono';

/**
 * Activación de la cuenta del portal y recuperación de contraseña (P-13): se pide el enlace con la
 * identificación, llega al correo registrado (vigente 24 h) y con él se define la contraseña.
 */
@Component({
  selector: 'app-enlace-cuenta',
  imports: [ReactiveFormsModule, RouterLink, CampoError, MarcoPublico, Icono],
  template: `
    <app-marco-publico>
      <section class="tarjeta angosta">
        <span class="icono-cabecera"><app-icono nombre="key-round" [tamano]="24" /></span>
        <h1>{{ titulo() }}</h1>
        @if (!token()) {
          @if (solicitado()) {
            <p>
              Si la identificación corresponde a un usuario registrado{{ tipoValido() === 'activacion' ? ' con la cuenta sin activar' : ' con la cuenta activa' }},
              enviamos un enlace a su correo. Es de un solo uso y vence en pocas horas (24 por defecto); si pide otro, el anterior deja de servir.
            </p>
          } @else {
            <form [formGroup]="solicitud" (ngSubmit)="solicitar()" novalidate>
              <label>Tipo de identificación
                <select formControlName="tipoIdentificacion">
                  @for (t of tipos; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
                </select>
              </label>
              <label>Número de identificación
                <input formControlName="numeroIdentificacion" maxlength="15" />
              </label>
              <app-campo-error [control]="solicitud.controls.numeroIdentificacion" />
              <button type="submit" class="bloque" [disabled]="enviando()">Enviar enlace a mi correo</button>
            </form>
          }
        } @else if (enlaceInvalido()) {
          <p class="aviso-inline peligro" role="alert">{{ enlaceInvalido() }}</p>
          <p><a [routerLink]="['/cuenta', tipoValido()]">Solicitar un enlace nuevo</a></p>
        } @else {
          <form [formGroup]="confirmacion" (ngSubmit)="confirmar()" novalidate>
            <p class="ayuda">La contraseña debe tener entre 8 y 64 caracteres, con mayúscula, minúscula, número y símbolo, y no puede contener su número de identificación.</p>
            <label>Nueva contraseña
              <input type="password" formControlName="password" autocomplete="new-password" maxlength="64" />
            </label>
            <app-campo-error [control]="confirmacion.controls.password" />
            <label>Confirme la contraseña
              <input type="password" formControlName="confirmacion" autocomplete="new-password" maxlength="64" />
            </label>
            <app-campo-error [control]="confirmacion.controls.confirmacion" />
            <button type="submit" class="bloque" [disabled]="enviando()">Guardar contraseña</button>
          </form>
        }
        <p class="ayuda"><a routerLink="/ingresar">Volver a ingresar</a></p>
      </section>
    </app-marco-publico>
  `,
})
export class EnlaceCuenta {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);

  /** Segmento de la ruta: activacion | recuperacion. */
  readonly tipo = input.required<string>();
  /** Token que llega en el enlace del correo (?token=...). */
  readonly token = input<string>();

  readonly tipos = TIPOS_IDENTIFICACION;
  readonly enviando = signal(false);
  readonly solicitado = signal(false);
  readonly enlaceInvalido = signal<string | null>(null);
  readonly titulo = computed(() => (this.tipoValido() === 'activacion' ? 'Activar mi cuenta' : 'Recuperar contraseña'));

  readonly solicitud = this.fb.group(
    { tipoIdentificacion: ['CC', Validators.required], numeroIdentificacion: ['', Validators.required] },
    { validators: documentoValido() },
  );

  readonly confirmacion = this.fb.group(
    { password: ['', [Validators.required, passwordSegura]], confirmacion: ['', Validators.required] },
    { validators: coinciden('password', 'confirmacion') },
  );

  tipoValido(): TipoEnlace {
    return this.tipo() === 'recuperacion' ? 'recuperacion' : 'activacion';
  }

  solicitar() {
    if (!listoParaEnviar(this.solicitud)) return;
    const v = this.solicitud.getRawValue();
    this.enviando.set(true);
    this.auth.solicitarEnlace(this.tipoValido(), v.tipoIdentificacion, v.numeroIdentificacion.trim().toUpperCase()).subscribe({
      next: () => this.solicitado.set(true),
      error: () => this.enviando.set(false),
    });
  }

  confirmar() {
    if (!listoParaEnviar(this.confirmacion)) return;
    const v = this.confirmacion.getRawValue();
    this.enviando.set(true);
    this.auth.confirmarEnlace(this.tipoValido(), this.token()!, v.password, v.confirmacion).subscribe({
      next: () => {
        this.avisos.exito('Contraseña guardada. Ya puede ingresar.');
        this.router.navigate(['/ingresar']);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        if (e.codigo === 'ENLACE_INVALIDO') {
          this.enlaceInvalido.set(e.mensaje);
        } else if (!aplicarErrorPassword(this.confirmacion, e, { nueva: 'password' })) {
          aplicarErroresServidor(this.confirmacion, e);
        }
      },
    });
  }
}
