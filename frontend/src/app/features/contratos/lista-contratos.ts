import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { CatalogosService } from '../../core/catalogos.service';
import { Pagina, TIPOS_SERVICIO } from '../../core/modelos';
import { Paginador } from '../../shared/paginador';
import { ContratoVista, ContratosService, ESTADOS_CONTRATO } from './contratos.service';

/** Consulta de contratos por número, municipio, servicio y estado (RF-04). */
@Component({
  selector: 'app-lista-contratos',
  imports: [ReactiveFormsModule, RouterLink, Paginador, DatePipe],
  template: `
    <div class="encabezado">
      <h1>Contratos</h1>
      @if (auth.tienePermiso('ORDEN_GESTIONAR')) { <a class="boton secundario" routerLink="ordenes">Órdenes pendientes</a> }
    </div>
    <p class="ayuda">Para registrar un contrato, abra la ficha del usuario titular.</p>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <input formControlName="numero" placeholder="Número (CTR-…)" maxlength="12" />
      <select formControlName="municipioId">
        <option [ngValue]="null">Todos los municipios</option>
        @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
      </select>
      <select formControlName="tipoServicio">
        <option value="">Todos los servicios</option>
        @for (t of servicios; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
      </select>
      <select formControlName="estado">
        <option value="">Todos los estados</option>
        @for (e of estados; track e) { <option [value]="e">{{ e }}</option> }
      </select>
      <button type="submit">Buscar</button>
    </form>
    <table class="tabla">
      <thead><tr><th>Número</th><th>Titular</th><th>Servicio</th><th>Predio</th><th>Municipio</th><th>Vinculación</th><th>Estado</th></tr></thead>
      <tbody>
        @for (c of pagina()?.content ?? []; track c.id) {
          <tr>
            <td><a [routerLink]="[c.id]">{{ c.numeroContrato }}</a></td>
            <td><a [routerLink]="['/usuarios', c.usuarioId]">{{ c.titular }}</a></td>
            <td>{{ c.tipoServicio }}</td><td>{{ c.codigoPredio }} · {{ c.direccion }}</td><td>{{ c.municipio }}</td>
            <td>{{ c.fechaVinculacion | date: 'mediumDate' }}</td>
            <td><span class="estado" [attr.data-estado]="c.estado">{{ c.estado }}</span></td>
          </tr>
        } @empty { <tr><td colspan="7">Sin resultados.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />
  `,
})
export class ListaContratos {
  private readonly servicio = inject(ContratosService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly servicios = TIPOS_SERVICIO;
  readonly estados = ESTADOS_CONTRATO;
  readonly pagina = signal<Pagina<ContratoVista> | null>(null);

  readonly filtros = this.fb.group({
    numero: ['', Validators.pattern(/^\s*[A-Za-z0-9-]{0,12}\s*$/)],
    municipioId: this.fb.control<number | null>(this.auth.sesion()?.municipioId ?? null),
    tipoServicio: [''],
    estado: [''],
  });

  constructor() {
    this.buscar(0);
  }

  buscar(page: number) {
    if (this.filtros.invalid) return;
    const v = this.filtros.getRawValue();
    this.servicio.buscar({ ...v, numero: v.numero.trim().toUpperCase(), page }).subscribe((p) => this.pagina.set(p));
  }
}
