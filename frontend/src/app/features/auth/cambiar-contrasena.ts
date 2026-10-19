import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { Icono } from '../../shared/icono';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { coinciden, passwordSegura } from '../../shared/validadores';
import { aplicarErrorPassword } from './errores-password';

@Component({
  selector: 'app-cambiar-contrasena',
  imports: [ReactiveFormsModule, CampoError, Icono],
  template: `
    <section class="tarjeta angosta">
      <span class="icono-cabecera"><app-icono nombre="lock" [tamano]="24" /></span>
      <h1>Cambiar contraseña</h1>
      @if (auth.sesion()?.cambioPasswordRequerido) {
        <p class="aviso-inline">Debe cambiar la contraseña temporal antes de continuar.</p>
      }
      <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
        <label>Contraseña actual
          <input type="password" formControlName="actual" autocomplete="current-password" maxlength="64" />
        </label>
        <app-campo-error [control]="form.controls.actual" />
        <label>Nueva contraseña
          <input type="password" formControlName="nueva" autocomplete="new-password" maxlength="64" />
        </label>
        <app-campo-error [control]="form.controls.nueva" />
        <label>Confirme la nueva contraseña
          <input type="password" formControlName="confirmacion" autocomplete="new-password" maxlength="64" />
        </label>
        <app-campo-error [control]="form.controls.confirmacion" />
        <button type="submit" class="bloque" [disabled]="enviando()">Guardar</button>
      </form>
    </section>
  `,
})
export class CambiarContrasena {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly enviando = signal(false);

  readonly form = this.fb.group(
    {
      actual: ['', Validators.required],
      nueva: ['', [Validators.required, passwordSegura]],
      confirmacion: ['', Validators.required],
    },
    { validators: coinciden('nueva', 'confirmacion') },
  );

  guardar() {
    if (!listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    if (v.actual === v.nueva) {
      this.form.controls.nueva.setErrors({ servidor: 'La nueva contraseña debe ser distinta de la actual' });
      return;
    }
    this.enviando.set(true);
    this.auth.cambiarPassword(v.actual, v.nueva, v.confirmacion).subscribe({
      // La respuesta trae una sesión nueva con permisos: no hace falta volver a ingresar
      next: () => {
        this.avisos.exito('Contraseña actualizada');
        this.router.navigate(['/inicio']);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        if (!aplicarErrorPassword(this.form, e)) {
          aplicarErroresServidor(this.form, e);
        }
      },
    });
  }
}
