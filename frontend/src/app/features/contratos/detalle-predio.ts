import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, hoyIso, listoParaEnviar } from '../../shared/formularios';
import { fechaNoFutura } from '../../shared/validadores';
import { PredioDetalle, UsuariosService } from '../usuarios/usuarios.service';
import { ContratosService, MedidorVista } from './contratos.service';
import { FormularioPredio } from './formulario-predio';

/** Ficha del predio: datos, medidores (instalación, reposición y retiro) y contratos del predio. */
@Component({
  selector: 'app-detalle-predio',
  imports: [RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule, CampoError, FormularioPredio],
  template: `
    @if (detalle(); as d) {
      <h1>Predio {{ d.predio.codigoPredio }}</h1>
      <dl class="ficha">
        <dt>Dirección</dt><dd>{{ d.predio.direccion }}{{ d.predio.barrioVereda ? ', ' + d.predio.barrioVereda : '' }}</dd>
        <dt>Municipio</dt><dd>{{ d.predio.municipio }}{{ d.predio.sector ? ' · ' + d.predio.sector : '' }}</dd>
        <dt>Uso</dt><dd>{{ d.predio.uso }}{{ d.predio.estrato ? ' · estrato ' + d.predio.estrato : '' }}</dd>
        <dt>Ficha catastral</dt><dd>{{ d.predio.fichaCatastral ?? '—' }}</dd>
      </dl>
      @if (auth.tienePermiso('PREDIO_GESTIONAR')) {
        <details><summary>Editar predio</summary>
          <div class="tarjeta"><app-formulario-predio [predio]="d.predio" (guardado)="recargar()" /></div>
        </details>
      }

      <h2>Medidores</h2>
      <table class="tabla">
        <thead><tr><th>Serial</th><th>Servicio</th><th>Marca</th><th>Instalación</th><th class="num">Lectura</th><th>Último periodo</th><th>Contrato</th><th>Estado</th><th></th></tr></thead>
        <tbody>
          @for (m of d.medidores; track m.id) {
            <tr>
              <td>{{ m.serial }}</td><td>{{ m.tipoServicio }}</td><td>{{ m.marca ?? '—' }}</td>
              <td>{{ m.fechaInstalacion | date: 'mediumDate' }}</td>
              <td class="num">{{ m.lecturaActual | number: '1.0-3' }}</td><td>{{ m.ultimoPeriodo ?? '—' }}</td>
              <td>{{ m.numeroContrato ?? '—' }}</td>
              <td>
                <span class="estado" [attr.data-estado]="m.estado === 'ACTIVO' ? 'ACTIVO' : 'CANCELADO'">{{ m.estado }}</span>
                @if (m.lecturaEnRevision) { <span class="etiqueta">Lectura en revisión</span> }
                @if (m.fechaRetiro) { <small>{{ m.fechaRetiro | date: 'mediumDate' }}</small> }
              </td>
              <td>
                @if (m.estado === 'ACTIVO' && auth.tienePermiso('MEDIDOR_GESTIONAR')) {
                  <button type="button" class="enlace" (click)="retirando.set(m)">Retirar</button>
                }
              </td>
            </tr>
          } @empty { <tr><td colspan="9">Sin medidores.</td></tr> }
        </tbody>
      </table>
      @if (retirando(); as m) {
        <form class="tarjeta" [formGroup]="retiro" (ngSubmit)="retirar(m)" novalidate>
          <h3>Retirar el medidor {{ m.serial }}</h3>
          <p class="ayuda">Instale después el medidor de reposición: el contrato vigente pasa a usarlo.</p>
          <label>Fecha de retiro <input type="date" formControlName="fechaRetiro" [max]="hoy" /></label>
          <app-campo-error [control]="retiro.controls.fechaRetiro" />
          <div class="acciones">
            <button type="submit" class="peligro">Retirar</button>
            <button type="button" class="secundario" (click)="retirando.set(null)">Cerrar</button>
          </div>
        </form>
      }
      @if (auth.tienePermiso('MEDIDOR_GESTIONAR')) {
        <details><summary>Instalar medidor</summary>
          <form class="tarjeta" [formGroup]="medidor" (ngSubmit)="instalar()" novalidate>
            <div class="fila">
              <label>Servicio
                <select formControlName="tipoServicio">
                  <option value="ACUEDUCTO">Acueducto</option>
                  <option value="GAS">Gas</option>
                </select>
              </label>
              <label>Serial <input formControlName="serial" maxlength="30" />
                <app-campo-error [control]="medidor.controls.serial" /></label>
              <label>Marca <input formControlName="marca" maxlength="60" /></label>
            </div>
            <div class="fila">
              <label>Instalación <input type="date" formControlName="fechaInstalacion" [max]="hoy" />
                <app-campo-error [control]="medidor.controls.fechaInstalacion" /></label>
              <label>Lectura inicial (m³) <input type="number" formControlName="lecturaInicial" min="0" step="0.001" />
                <app-campo-error [control]="medidor.controls.lecturaInicial" /></label>
            </div>
            <p class="ayuda">El alcantarillado no lleva medidor: se liquida con la lectura del acueducto.</p>
            <button type="submit">Instalar</button>
          </form>
        </details>
      }

      <h2>Contratos del predio</h2>
      <table class="tabla">
        <thead><tr><th>Número</th><th>Titular</th><th>Servicio</th><th>Medidor</th><th>Estado</th></tr></thead>
        <tbody>
          @for (c of d.contratos; track c.id) {
            <tr>
              <td><a [routerLink]="['/contratos', c.id]">{{ c.numeroContrato }}</a></td>
              <td><a [routerLink]="['/usuarios', c.usuarioId]">{{ c.titular }}</a></td>
              <td>{{ c.tipoServicio }}</td><td>{{ c.serialMedidor ?? '—' }}</td>
              <td><span class="estado" [attr.data-estado]="c.estado">{{ c.estado }}</span></td>
            </tr>
          } @empty { <tr><td colspan="5">Sin contratos.</td></tr> }
        </tbody>
      </table>
    }
  `,
})
export class DetallePredio {
  private readonly usuarios = inject(UsuariosService);
  private readonly contratos = inject(ContratosService);
  private readonly avisos = inject(AvisosService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);
  readonly hoy = hoyIso();

  readonly id = input.required<string>();
  readonly detalle = signal<PredioDetalle | null>(null);
  readonly retirando = signal<MedidorVista | null>(null);

  readonly medidor = this.fb.group({
    tipoServicio: ['ACUEDUCTO', Validators.required],
    serial: ['', [Validators.required, Validators.pattern(/^[A-Z0-9-]{4,30}$/)]],
    marca: ['', Validators.maxLength(60)],
    fechaInstalacion: [this.hoy, [Validators.required, fechaNoFutura]],
    lecturaInicial: [0, [Validators.required, Validators.min(0), Validators.max(999999999)]],
  });
  readonly retiro = this.fb.group({ fechaRetiro: [this.hoy, [Validators.required, fechaNoFutura]] });

  constructor() {
    this.medidor.controls.serial.valueChanges.subscribe((s) => {
      const mayus = s.toUpperCase();
      if (s !== mayus) this.medidor.controls.serial.setValue(mayus, { emitEvent: false });
    });
  }

  ngOnInit() {
    this.recargar();
  }

  recargar() {
    this.usuarios.predio(Number(this.id())).subscribe((d) => this.detalle.set(d));
  }

  instalar() {
    if (!listoParaEnviar(this.medidor)) return;
    const v = this.medidor.getRawValue();
    this.contratos.instalarMedidor(Number(this.id()), { ...v, marca: v.marca.trim() || null }).subscribe({
      next: (m) => {
        this.avisos.exito(`Medidor ${m.serial} instalado${m.numeroContrato ? ' y asignado al contrato ' + m.numeroContrato : ''}`);
        this.medidor.reset();
        this.recargar();
      },
      error: (e: ApiError) => {
        aplicarErroresServidor(this.medidor, e);
        if (e.codigo === 'DUPLICADO') this.medidor.controls.serial.setErrors({ servidor: e.mensaje });
      },
    });
  }

  retirar(m: MedidorVista) {
    if (!listoParaEnviar(this.retiro)) return;
    this.contratos.retirarMedidor(m.id, this.retiro.getRawValue().fechaRetiro, m.version).subscribe(() => {
      this.avisos.exito(`Medidor ${m.serial} retirado`);
      this.retirando.set(null);
      this.recargar();
    });
  }
}
