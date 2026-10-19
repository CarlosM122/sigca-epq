import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AvisosService } from '../../core/avisos.service';
import { CampoError } from '../../shared/campo-error';
import { listoParaEnviar } from '../../shared/formularios';
import { periodo } from '../../shared/validadores';
import { FacturacionService, LoteVista, periodoAnterior } from './facturacion.service';

/**
 * RF-05: lanza o relanza el lote de un periodo cerrado y muestra su resultado. Un lote con predios en error queda
 * FALLIDO y se puede relanzar: solo factura lo que quedó pendiente.
 */
@Component({
  selector: 'app-lotes',
  imports: [ReactiveFormsModule, CampoError, CurrencyPipe, DatePipe, RouterLink],
  template: `
    <div class="encabezado">
      <h1>Lotes de facturación</h1>
      <a routerLink="/facturacion/facturas">Ver facturas</a>
    </div>
    <form class="tarjeta filtros" [formGroup]="form" (ngSubmit)="generar()" novalidate>
      <label>Periodo a facturar (AAAAMM)
        <input formControlName="periodo" maxlength="6" [placeholder]="'Último cerrado: ' + anterior" />
      </label>
      <button type="submit" [disabled]="generando()">{{ generando() ? 'Facturando…' : 'Generar lote' }}</button>
      <app-campo-error [control]="form.controls.periodo" />
      <p class="ayuda">Si lo deja vacío se factura el último periodo cerrado ({{ anterior }}). El lote automático corre el primer día hábil de cada mes.</p>
    </form>

    @if (seleccionado(); as l) {
      <section class="tarjeta" aria-live="polite">
        <h2>Lote #{{ l.id }} · periodo {{ l.periodo }} <span class="estado" [attr.data-estado]="l.estado">{{ l.estado }}</span></h2>
        <dl class="ficha">
          <dt>Inicio / fin</dt><dd>{{ l.iniciadoEn | date: 'medium' }} / {{ l.finalizadoEn ? (l.finalizadoEn | date: 'medium') : 'en proceso' }}</dd>
          <dt>Ejecutado por</dt><dd>{{ l.ejecutadoPor }}</dd>
          <dt>Predios procesados</dt><dd>{{ l.totalPredios }}</dd>
          <dt>Facturas emitidas</dt><dd>{{ l.totalFacturas }}</dd>
          <dt>Predios con error</dt><dd>{{ l.totalErrores }}</dd>
          <dt>Valor facturado</dt><dd>{{ l.valorTotal | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</dd>
        </dl>
        @if (l.errores.length) {
          <h3>Predios sin facturar</h3>
          <table class="tabla">
            <thead><tr><th>Predio</th><th>Motivo</th></tr></thead>
            <tbody>
              @for (e of l.errores; track $index) {
                <tr><td>{{ e.codigoPredio ?? '—' }}</td><td>{{ e.mensaje }}</td></tr>
              }
            </tbody>
          </table>
          @if (l.estado === 'FALLIDO') {
            <p class="ayuda">Corrija las causas (por ejemplo, revise las lecturas EN_REVISION) y relance el periodo {{ l.periodo }}.</p>
            <button type="button" (click)="relanzar(l.periodo)" [disabled]="generando()">Relanzar periodo {{ l.periodo }}</button>
          }
        }
      </section>
    }

    <h2>Lotes recientes</h2>
    <table class="tabla">
      <thead><tr><th>#</th><th>Periodo</th><th>Inicio</th><th>Facturas</th><th>Errores</th><th>Valor</th><th>Estado</th><th></th></tr></thead>
      <tbody>
        @for (l of lotes(); track l.id) {
          <tr>
            <td>{{ l.id }}</td><td>{{ l.periodo }}</td><td>{{ l.iniciadoEn | date: 'short' }}</td>
            <td class="num">{{ l.totalFacturas }}/{{ l.totalPredios }}</td><td class="num">{{ l.totalErrores }}</td>
            <td class="num">{{ l.valorTotal | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
            <td><span class="estado" [attr.data-estado]="l.estado">{{ l.estado }}</span></td>
            <td><button type="button" class="enlace" (click)="seleccionado.set(l)">Detalle</button></td>
          </tr>
        } @empty { <tr><td colspan="8">Aún no se han generado lotes.</td></tr> }
      </tbody>
    </table>
  `,
})
export class Lotes {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(FacturacionService);
  private readonly avisos = inject(AvisosService);
  readonly anterior = periodoAnterior();
  readonly lotes = signal<LoteVista[]>([]);
  readonly seleccionado = signal<LoteVista | null>(null);
  readonly generando = signal(false);
  readonly form = this.fb.group({ periodo: ['', periodo] });

  constructor() {
    this.cargar();
  }

  private cargar() {
    this.servicio.lotes().subscribe((l) => this.lotes.set(l));
  }

  generar() {
    if (!listoParaEnviar(this.form)) return;
    const p = this.form.getRawValue().periodo;
    this.ejecutar(p ? Number(p) : null);
  }

  relanzar(periodoLote: number) {
    this.ejecutar(periodoLote);
  }

  private ejecutar(p: number | null) {
    this.generando.set(true);
    this.servicio.generarLote(p).subscribe({
      next: (l) => {
        this.generando.set(false);
        this.seleccionado.set(l);
        if (l.estado === 'COMPLETADO') {
          this.avisos.exito(`Lote ${l.periodo}: ${l.totalFacturas} facturas emitidas`);
        } else {
          this.avisos.error(`Lote ${l.periodo}: ${l.totalFacturas} facturas emitidas y ${l.totalErrores} predios con error`);
        }
        this.cargar();
      },
      error: () => this.generando.set(false),
    });
  }
}
