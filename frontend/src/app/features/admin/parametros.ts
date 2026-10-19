import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';

import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { sinHtml } from '../../shared/validadores';
import { AdminService, ParametroVista, TipoDato } from './admin.service';

/** Mismo formato que CK_PARAMETRO_* y la validación del backend; los rangos legales los valida la BD. */
export function validadorParametro(tipo: TipoDato, clave: string): ValidatorFn[] {
  switch (tipo) {
    case 'ENTERO':
      return [Validators.required, Validators.pattern(/^-?\d{1,9}$/)];
    case 'DECIMAL':
      return [Validators.required, Validators.pattern(/^-?\d{1,9}(\.\d{1,4})?$/)];
    case 'BOOLEANO':
      return [Validators.required, Validators.pattern(/^[SN]$/)];
    default:
      return clave === 'MODO_OPERACION'
        ? [Validators.required, Validators.pattern(/^(NORMAL|MANTENIMIENTO|CONTINGENCIA)$/)]
        : [Validators.required, Validators.maxLength(400), sinHtml];
  }
}

/** RF-29: parámetros del sistema. Cada cambio queda auditado con el valor anterior y el nuevo. */
@Component({
  selector: 'app-parametros',
  imports: [ReactiveFormsModule, CampoError, DatePipe],
  template: `
    <h1>Parámetros del sistema</h1>
    <p class="ayuda">Los rangos legales (plazos de PQR, cuotas, tasas, periodos de mora) los valida la base de datos al guardar.</p>
    <table class="tabla">
      <thead><tr><th>Parámetro</th><th>Valor</th><th>Actualizado</th><th></th></tr></thead>
      <tbody>
        @for (p of parametros(); track p.clave) {
          <tr>
            <td><strong>{{ p.clave }}</strong><br /><small>{{ p.descripcion }}</small></td>
            <td>
              @if (editando() === p.clave) {
                @if (p.tipoDato === 'BOOLEANO') {
                  <select [formControl]="valor"><option value="S">S</option><option value="N">N</option></select>
                } @else if (p.clave === 'MODO_OPERACION') {
                  <select [formControl]="valor">
                    <option value="NORMAL">NORMAL</option><option value="MANTENIMIENTO">MANTENIMIENTO</option>
                    <option value="CONTINGENCIA">CONTINGENCIA</option>
                  </select>
                } @else {
                  <input [formControl]="valor" [attr.inputmode]="p.tipoDato === 'TEXTO' ? null : 'decimal'" maxlength="400" />
                }
                <app-campo-error [control]="valor" />
              } @else {
                <code>{{ p.valor }}</code> <small>{{ p.tipoDato }}</small>
              }
            </td>
            <td><small>{{ p.actualizadoEn | date: 'short' }} · {{ p.actualizadoPor }}</small></td>
            <td>
              @if (editando() === p.clave) {
                <button type="button" (click)="guardar(p)" [disabled]="enviando()">Guardar</button>
                <button type="button" class="secundario" (click)="editando.set(null)">Cancelar</button>
              } @else {
                <button type="button" class="enlace" (click)="editar(p)">Modificar</button>
              }
            </td>
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class Parametros {
  private readonly servicio = inject(AdminService);
  private readonly avisos = inject(AvisosService);
  readonly parametros = signal<ParametroVista[]>([]);
  readonly editando = signal<string | null>(null);
  readonly enviando = signal(false);
  readonly valor = new FormControl('', { nonNullable: true });

  constructor() {
    this.servicio.parametros().subscribe((l) => this.parametros.set(l));
  }

  editar(p: ParametroVista) {
    this.valor.setValidators(validadorParametro(p.tipoDato, p.clave));
    this.valor.reset(p.valor);
    this.editando.set(p.clave);
  }

  guardar(p: ParametroVista) {
    this.valor.markAsTouched();
    if (this.valor.invalid) return;
    this.enviando.set(true);
    this.servicio.modificarParametro(p.clave, this.valor.value.trim()).subscribe({
      next: (n) => {
        this.enviando.set(false);
        this.parametros.update((l) => l.map((x) => (x.clave === n.clave ? n : x)));
        this.editando.set(null);
        this.avisos.exito(`Parámetro ${n.clave} actualizado`);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        this.valor.setErrors({ servidor: e.mensaje });
      },
    });
  }
}
