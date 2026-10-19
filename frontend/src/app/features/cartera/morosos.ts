import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { CatalogosService } from '../../core/catalogos.service';
import { Pagina } from '../../core/modelos';
import { Paginador } from '../../shared/paginador';
import { CarteraService, Cobro, Moroso, guardarArchivo } from './cartera.service';

/**
 * Cartera morosa y cobro persuasivo (RF-23, P-11): filtros por municipio, antigüedad y etapa del cobro; acciones
 * en orden (iniciar cobro → candidato → aviso previo → orden de suspensión) y acceso al acuerdo de pago.
 */
@Component({
  selector: 'app-morosos',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, Paginador],
  template: `
    <div class="encabezado">
      <h1>Cartera morosa</h1>
      <div class="acciones">
        <a class="boton secundario" routerLink="acuerdos">Acuerdos de pago</a>
        @if (auth.tienePermiso('REPORTE_CARTERA')) {
          <button type="button" class="secundario" (click)="exportar()" [disabled]="exportando()">Exportar CSV</button>
        }
      </div>
    </div>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <input formControlName="texto" placeholder="Código o nombre" maxlength="60" aria-label="Código o nombre" />
      <select formControlName="municipioId" aria-label="Municipio">
        <option [ngValue]="null">Todos los municipios</option>
        @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
      </select>
      <select formControlName="antiguedad" aria-label="Antigüedad">
        <option value="">Cualquier antigüedad</option>
        <option value="30">Hasta 30 días</option>
        <option value="60">31 a 60 días</option>
        <option value="90">61 a 90 días</option>
        <option value="MAS_90">Más de 90 días</option>
      </select>
      <select formControlName="cobro" aria-label="Etapa del cobro">
        <option value="">Cualquier etapa</option>
        <option value="SIN_COBRO">Sin cobro</option>
        <option value="ACTIVO">Cobro activo</option>
        <option value="CANDIDATO">Candidato a suspensión</option>
        <option value="AVISADO">Avisado</option>
        <option value="EN_ACUERDO">Cobro suspendido por acuerdo</option>
      </select>
      <select formControlName="sort" aria-label="Orden">
        <option value="deuda,desc">Mayor deuda</option>
        <option value="dias,desc">Más días de mora</option>
        <option value="periodos,desc">Más periodos</option>
        <option value="nombre,asc">Nombre</option>
      </select>
      <button type="submit">Filtrar</button>
    </form>
    <table class="tabla">
      <thead>
        <tr><th>Usuario</th><th>Municipio</th><th>Facturas</th><th>Periodos</th><th>Días</th><th>Deuda exigible</th><th>Cobro</th><th>Acciones</th></tr>
      </thead>
      <tbody>
        @for (m of pagina()?.content ?? []; track m.usuarioId) {
          <tr>
            <td>{{ m.codigoUsuario }}<br /><small>{{ m.nombre }}</small></td>
            <td>{{ m.municipios }}</td>
            <td>{{ m.facturasVencidas }}</td>
            <td>{{ m.periodosMora }}</td>
            <td>{{ m.diasMora }}</td>
            <td class="num">{{ m.deudaExigible | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}
              @if (m.valorEnReclamo > 0) { <br /><small>{{ m.valorEnReclamo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} en reclamo</small> }</td>
            <td>
              @if (!m.cobroId) { <span class="etiqueta">Sin cobro</span> }
              @else if (m.estadoCobro === 'SUSPENDIDO') { <span class="etiqueta">Suspendido (acuerdo)</span> }
              @else {
                <span class="etiqueta">Desde {{ m.fechaInicioCobro | date: 'mediumDate' }}</span>
                @if (m.candidatoSuspension) { <span class="etiqueta peligro">Candidato</span> }
                @if (m.fechaAvisoSuspension) { <span class="etiqueta peligro">Avisado {{ m.fechaAvisoSuspension | date: 'shortDate' }}</span> }
                @if (m.ordenesSuspensionPendientes) { <span class="etiqueta peligro">{{ m.ordenesSuspensionPendientes }} orden(es)</span> }
              }
              @if (m.acuerdoVigente) { <span class="etiqueta">Acuerdo vigente</span> }
            </td>
            <td>
              @if (auth.tienePermiso('COBRO_GESTIONAR')) {
                @if (!m.cobroId) {
                  <button type="button" class="enlace" (click)="accion(m, 'iniciar')">Iniciar cobro</button>
                } @else if (m.estadoCobro === 'ACTIVO') {
                  @if (!m.candidatoSuspension) {
                    <button type="button" class="enlace" (click)="accion(m, 'candidato')" [disabled]="!m.puedeSerCandidato"
                      [title]="m.puedeSerCandidato ? '' : 'No completa los periodos de mora exigidos'">Marcar candidato</button>
                  } @else if (!m.fechaAvisoSuspension) {
                    <button type="button" class="enlace" (click)="accion(m, 'aviso')">Enviar aviso</button>
                  } @else if (!m.acuerdoVigente && m.contratosActivos > 0 && !m.ordenesSuspensionPendientes) {
                    <button type="button" class="enlace" (click)="accion(m, 'orden')">Ordenar suspensión</button>
                  }
                }
              }
              @if (auth.tienePermiso('ACUERDO_GESTIONAR') && !m.acuerdoVigente) {
                <a [routerLink]="['acuerdo', m.usuarioId]">Acuerdo de pago</a>
              }
            </td>
          </tr>
        } @empty { <tr><td colspan="8">Sin usuarios en mora con esos filtros.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />
  `,
})
export class Morosos {
  private readonly servicio = inject(CarteraService);
  private readonly avisos = inject(AvisosService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly pagina = signal<Pagina<Moroso> | null>(null);
  readonly exportando = signal(false);
  readonly filtros = this.fb.group({
    texto: [''],
    municipioId: this.fb.control<number | null>(null),
    antiguedad: [''],
    cobro: [''],
    sort: ['deuda,desc'],
  });

  constructor() {
    this.buscar(0);
  }

  buscar(page: number) {
    const v = this.filtros.getRawValue();
    this.servicio.morosos({ ...v, texto: v.texto.trim(), page }).subscribe((p) => this.pagina.set(p));
  }

  accion(m: Moroso, tipo: 'iniciar' | 'candidato' | 'aviso' | 'orden') {
    const textos = {
      iniciar: `¿Iniciar el cobro persuasivo de ${m.nombre}?`,
      candidato: `¿Marcar a ${m.nombre} como candidato a suspensión (${m.periodosMora} periodos en mora)?`,
      aviso: `¿Enviar a ${m.nombre} el aviso previo de suspensión por correo y SMS?`,
      orden: `¿Ordenar la suspensión del servicio de ${m.nombre}? Se genera una orden por cada contrato activo con mora.`,
    };
    if (!window.confirm(textos[tipo])) return;
    let op: Observable<Cobro>;
    switch (tipo) {
      case 'iniciar':
        op = this.servicio.iniciarCobro(m.usuarioId, null);
        break;
      case 'candidato':
        op = this.servicio.marcarCandidato(m.cobroId!, null);
        break;
      case 'aviso':
        op = this.servicio.avisarSuspension(m.cobroId!, null);
        break;
      default:
        op = this.servicio.ordenarSuspension(m.cobroId!, null);
    }
    op.subscribe((c) => {
      const mensajes = {
        iniciar: 'Cobro persuasivo iniciado',
        candidato: 'Usuario marcado como candidato a suspensión',
        aviso: 'Aviso de suspensión enviado',
        orden: `Órdenes generadas: ${c.ordenes.filter((o) => o.estado === 'PENDIENTE').map((o) => o.numeroOrden).join(', ')}`,
      };
      this.avisos.exito(mensajes[tipo]);
      this.buscar(this.pagina()?.page.number ?? 0);
    });
  }

  exportar() {
    this.exportando.set(true);
    const v = this.filtros.getRawValue();
    this.servicio.exportarMorosos({ municipioId: v.municipioId, antiguedad: v.antiguedad, cobro: v.cobro, texto: v.texto.trim() }).subscribe({
      next: (b) => {
        this.exportando.set(false);
        guardarArchivo(b, `morosos-${new Date().toISOString().slice(0, 10)}.csv`);
      },
      error: () => this.exportando.set(false),
    });
  }
}
