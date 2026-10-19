import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';

import { ConsumosContrato, ContratoPortal, PortalService } from './portal.service';

/** P-29, RF-27: contratos activos y suspendidos del titular y el historial de consumos de cada uno. */
@Component({
  selector: 'app-mis-contratos',
  imports: [DatePipe, DecimalPipe],
  template: `
    <h1>Mis contratos</h1>
    <table class="tabla">
      <thead><tr><th>Número</th><th>Servicio</th><th>Predio</th><th>Medidor</th><th>Desde</th><th>Estado</th><th></th></tr></thead>
      <tbody>
        @for (c of contratos(); track c.id) {
          <tr>
            <td>{{ c.numeroContrato }}</td><td>{{ c.tipoServicio }}</td>
            <td>{{ c.direccion }}, {{ c.municipio }} <small class="ayuda">{{ c.codigoPredio }}</small></td>
            <td>{{ c.serialMedidor ?? '—' }}</td><td>{{ c.fechaVinculacion | date: 'mediumDate' }}</td>
            <td><span class="estado" [attr.data-estado]="c.estado">{{ c.estado }}</span></td>
            <td><button type="button" class="enlace" (click)="verConsumos(c)" [attr.aria-pressed]="consumos()?.contratoId === c.id">Consumos</button></td>
          </tr>
        } @empty { <tr><td colspan="7">No tiene contratos activos.</td></tr> }
      </tbody>
    </table>

    @if (consumos(); as h) {
      <section class="tarjeta" aria-live="polite">
        <h2>Consumos del contrato {{ h.numeroContrato }} ({{ h.tipoServicio }})</h2>
        @if (h.serialMedidor) { <p class="ayuda">Medidor {{ h.serialMedidor }}@if (h.promedio !== null && h.promedio !== undefined) { · promedio {{ h.promedio | number: '1.0-2' }} m³ } </p> }
        @if (h.nota) { <p class="ayuda">{{ h.nota }}</p> }
        @for (c of h.consumos; track c.periodo) {
          <div class="barra-dato">
            <span>{{ c.periodo }}</span>
            <div class="barra-fondo"><div class="barra-valor" [style.width.%]="porcentaje(c.consumo)"></div></div>
            <span>{{ c.consumo !== null && c.consumo !== undefined ? (c.consumo | number: '1.0-3') + ' m³' : '—' }}
              @if (c.estado !== 'CONFIRMADA') { <span class="etiqueta">{{ c.estado === 'ESTIMADA' ? 'Estimado' : 'En revisión' }}</span> }</span>
          </div>
        } @empty { <p class="vacio">No hay lecturas en el periodo de consulta.</p> }
      </section>
    }
  `,
})
export class MisContratos {
  private readonly portal = inject(PortalService);
  readonly contratos = signal<ContratoPortal[]>([]);
  readonly consumos = signal<ConsumosContrato | null>(null);
  private readonly maximo = computed(() =>
    Math.max(1, ...(this.consumos()?.consumos ?? []).map((c) => c.consumo ?? 0)),
  );

  constructor() {
    this.portal.contratos().subscribe((c) => this.contratos.set(c));
  }

  verConsumos(c: ContratoPortal) {
    this.portal.consumos(c.id).subscribe((h) => this.consumos.set(h));
  }

  porcentaje(consumo?: number | null) {
    return consumo ? Math.round((consumo / this.maximo()) * 100) : 0;
  }
}
