import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { CatalogosService } from '../../core/catalogos.service';
import { Pagina, TIPOS_PQR } from '../../core/modelos';
import { Paginador } from '../../shared/paginador';
import { ESTADOS_PQR, PqrResumen, PqrService } from './pqr.service';

/**
 * Bandeja de trámite (RF-09, RF-11): el gestor ve su municipio y lo que tiene asignado; el jefe y quien tiene
 * PQR_CONSULTAR_TODAS ven todo. Ordenada por fecha límite, con días hábiles restantes y alertas.
 */
@Component({
  selector: 'app-bandeja-pqr',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, Paginador],
  template: `
    <div class="encabezado">
      <h1>PQR</h1>
      @if (auth.tienePermiso('PQR_REGISTRAR_PRESENCIAL')) {
        <a class="boton" routerLink="presencial">Radicar en ventanilla</a>
      }
    </div>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <input formControlName="radicado" placeholder="Radicado" maxlength="11" aria-label="Radicado" />
      <select formControlName="estado" aria-label="Estado">
        <option value="">Todos los estados</option>
        @for (e of estados; track e) { <option [value]="e">{{ e }}</option> }
      </select>
      <select formControlName="tipo" aria-label="Tipo">
        <option value="">Todos los tipos</option>
        @for (t of tipos; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
      </select>
      <select formControlName="vencimiento" aria-label="Vencimiento">
        <option value="">Cualquier plazo</option>
        <option value="POR_VENCER">Por vencer</option>
        <option value="VENCIDAS">Vencidas</option>
        <option value="SILENCIO">Silencio sin reconocer</option>
      </select>
      @if (auth.tienePermiso('PQR_CONSULTAR_TODAS')) {
        <select formControlName="municipioId" aria-label="Municipio">
          <option [ngValue]="null">Todos los municipios</option>
          @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
        </select>
      }
      <button type="submit">Buscar</button>
    </form>
    <table class="tabla">
      <thead>
        <tr><th>Radicado</th><th>Tipo</th><th>Estado</th><th>Municipio</th><th>Contrato</th><th>Gestor</th><th>Fecha límite</th><th>Días hábiles</th></tr>
      </thead>
      <tbody>
        @for (p of pagina()?.content ?? []; track p.id) {
          <tr [class.alerta]="alerta(p)">
            <td><a [routerLink]="[p.id]">{{ p.numeroRadicado }}</a></td>
            <td>{{ p.tipo }}<br /><small>{{ p.canal }}</small></td>
            <td><span class="estado" [attr.data-estado]="p.estado">{{ p.estado }}</span>
              @if (p.silencioPositivo) { <span class="etiqueta peligro">Silencio positivo</span> }</td>
            <td>{{ p.municipio }}</td>
            <td>{{ p.numeroContrato ?? '—' }}</td>
            <td>{{ p.gestor }}</td>
            <td>{{ p.fechaLimiteRespuesta | date: 'mediumDate' }}</td>
            <td>
              @if (p.diasHabilesRestantes !== null && p.diasHabilesRestantes !== undefined) {
                {{ p.diasHabilesRestantes < 0 ? 'Vencida hace ' + -p.diasHabilesRestantes : p.diasHabilesRestantes }}
              } @else { — }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="8">No hay PQR con esos filtros.</td></tr>
        }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />
  `,
})
export class Bandeja {
  private readonly servicio = inject(PqrService);
  readonly auth = inject(AuthService);
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly tipos = TIPOS_PQR;
  readonly estados = ESTADOS_PQR;
  readonly pagina = signal<Pagina<PqrResumen> | null>(null);

  private readonly fb = inject(NonNullableFormBuilder);
  readonly filtros = this.fb.group({
    radicado: [''],
    estado: [''],
    tipo: [''],
    vencimiento: [''],
    municipioId: this.fb.control<number | null>(null),
  });

  constructor() {
    this.buscar(0);
  }

  alerta(p: PqrResumen) {
    const abierta = p.estado === 'RECIBIDA' || p.estado === 'EN_TRAMITE' || p.estado === 'VENCIDA';
    return abierta && (p.diasHabilesRestantes ?? 99) <= 2;
  }

  buscar(page: number) {
    const v = this.filtros.getRawValue();
    this.servicio.bandeja({ ...v, radicado: v.radicado.trim(), page }).subscribe((p) => this.pagina.set(p));
  }
}
