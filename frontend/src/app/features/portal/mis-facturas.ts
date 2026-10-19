import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';

import { AvisosService } from '../../core/avisos.service';
import { Pagina } from '../../core/modelos';
import { Paginador } from '../../shared/paginador';
import { FacturaDetalle, descargarBlob } from '../facturacion/facturacion.service';
import { PagoResumen, TipoObligacion, urlCheckoutSegura } from '../pagos/pagos.service';
import { AcuerdoPortal, PortalService } from './portal.service';

/** RF-07, CU-04: facturas del titular con sus líneas, PDF, pago en línea, cuotas de acuerdos y pagos hechos. */
@Component({
  selector: 'app-mis-facturas',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, Paginador],
  template: `
    <h1>Mis facturas</h1>
    @if (disponible(); as d) {
      <p class="ayuda">Su próxima factura estará disponible hacia el {{ d | date: 'longDate' }}.</p>
    }
    <table class="tabla">
      <thead><tr><th>Factura</th><th>Periodo</th><th>Predio</th><th>Vence</th><th>Total</th><th>A pagar</th><th>Estado</th><th></th></tr></thead>
      <tbody>
        @for (f of facturas(); track f.id) {
          <tr>
            <td>{{ f.numeroFactura }}</td><td>{{ f.periodo }}</td><td>{{ f.direccion }}, {{ f.municipio }}</td>
            <td>{{ f.fechaVencimiento | date: 'mediumDate' }}</td>
            <td class="num">{{ f.totalAPagar | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
            <td class="num">{{ f.valorExigible | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}
              @if (f.valorEnReclamo > 0) { <span class="etiqueta">{{ f.valorEnReclamo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} en reclamo</span> }</td>
            <td><span class="estado" [attr.data-estado]="f.estado">{{ f.estado }}</span></td>
            <td>
              <button type="button" class="enlace" (click)="alternar(f.id)" [attr.aria-expanded]="abierta() === f.id">Detalle</button>
              <button type="button" class="enlace" (click)="pdf(f)">PDF</button>
              @if (pagable(f)) {
                <button type="button" (click)="pagar('FACTURA', f.id)" [disabled]="redirigiendo()">Pagar en línea</button>
              }
            </td>
          </tr>
          @if (abierta() === f.id) {
            <tr>
              <td colspan="8">
                <table class="tabla">
                  <thead><tr><th>Servicio</th><th>Contrato</th><th>Consumo m³</th><th>Cargo fijo</th><th>Consumo</th><th>Subsidio / contribución</th><th>Total</th></tr></thead>
                  <tbody>
                    @for (l of f.lineas; track l.id) {
                      <tr>
                        <td>{{ l.tipoServicio }}@if (l.enReclamo) { <span class="etiqueta">En reclamo</span> }</td><td>{{ l.numeroContrato }}</td>
                        <td class="num">{{ l.consumo | number: '1.0-3' }}@if (l.consumoEstimado) { <span class="etiqueta">Estimado</span> }</td>
                        <td class="num">{{ l.cargoFijo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                        <td class="num">{{ l.valorConsumo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                        <td class="num">{{ l.subsidio + l.contribucion | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                        <td class="num">{{ l.totalLinea | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
                @if (f.estado === 'EN_ACUERDO') { <p class="ayuda">Esta factura está incluida en un acuerdo de pago: pague las cuotas del acuerdo.</p> }
                @if (f.reexpedidaComo) { <p class="ayuda">Anulada y reexpedida como {{ f.reexpedidaComo }}.</p> }
              </td>
            </tr>
          }
        } @empty { <tr><td colspan="8">No tiene facturas en los últimos meses.</td></tr> }
      </tbody>
    </table>

    @if (acuerdos().length) {
      <h2>Acuerdos de pago</h2>
      @for (a of acuerdos(); track a.id) {
        <section class="tarjeta">
          <h3>{{ a.numeroAcuerdo }} <span class="estado" [attr.data-estado]="a.estado">{{ a.estado }}</span></h3>
          <p class="ayuda">Firmado el {{ a.fechaAcuerdo | date: 'mediumDate' }} · {{ a.numeroCuotas }} cuotas · total {{ a.totalIncluido | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
          <table class="tabla">
            <thead><tr><th>Cuota</th><th>Referencia</th><th>Vence</th><th>Valor</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (c of a.cuotas; track c.id) {
                <tr>
                  <td>{{ c.numeroCuota }}</td><td>{{ c.referencia }}</td><td>{{ c.fechaVencimiento | date: 'mediumDate' }}</td>
                  <td class="num">{{ c.valorCuota | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                  <td><span class="estado" [attr.data-estado]="c.estado">{{ c.estado }}</span></td>
                  <td>@if (c.pagable) { <button type="button" (click)="pagar('CUOTA', c.id)" [disabled]="redirigiendo()">Pagar en línea</button> }</td>
                </tr>
              }
            </tbody>
          </table>
        </section>
      }
    }

    <h2>Mis pagos</h2>
    <table class="tabla">
      <thead><tr><th>Comprobante</th><th>Fecha</th><th>Referencia</th><th>Canal</th><th>Valor</th><th></th></tr></thead>
      <tbody>
        @for (p of pagos()?.content ?? []; track p.id) {
          <tr>
            <td>{{ p.codigoPago }}</td><td>{{ p.fechaPago | date: 'medium' }}</td><td>{{ p.referencia }}</td><td>{{ p.canal }}</td>
            <td class="num">{{ p.valor | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
            <td><button type="button" class="enlace" (click)="comprobante(p)">Descargar</button></td>
          </tr>
        } @empty { <tr><td colspan="6">Aún no tiene pagos registrados.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagos()" (cambiar)="cargarPagos($event)" />
  `,
})
export class MisFacturas {
  private readonly portal = inject(PortalService);
  private readonly avisos = inject(AvisosService);
  readonly facturas = signal<FacturaDetalle[]>([]);
  readonly acuerdos = signal<AcuerdoPortal[]>([]);
  readonly pagos = signal<Pagina<PagoResumen> | null>(null);
  readonly disponible = signal<string | null>(null);
  readonly abierta = signal<number | null>(null);
  readonly redirigiendo = signal(false);

  constructor() {
    this.portal.facturas().subscribe((f) => this.facturas.set(f));
    this.portal.acuerdos().subscribe((a) => this.acuerdos.set(a));
    this.portal.disponibilidad().subscribe((d) => this.disponible.set(d.fechaEstimada));
    this.cargarPagos(0);
  }

  cargarPagos(page: number) {
    this.portal.pagos(page).subscribe((p) => this.pagos.set(p));
  }

  pagable(f: FacturaDetalle) {
    return (f.estado === 'PENDIENTE' || f.estado === 'VENCIDA') && f.valorExigible > 0;
  }

  alternar(id: number) {
    this.abierta.set(this.abierta() === id ? null : id);
  }

  pdf(f: FacturaDetalle) {
    this.portal.pdf(f.id).subscribe((b) => descargarBlob(b, `${f.numeroFactura}.pdf`));
  }

  comprobante(p: PagoResumen) {
    this.portal.comprobante(p.id).subscribe((b) => descargarBlob(b, `${p.codigoPago}.pdf`));
  }

  pagar(tipo: TipoObligacion, id: number) {
    this.redirigiendo.set(true);
    this.portal.pagar(tipo, id).subscribe({
      next: (r) => {
        if (urlCheckoutSegura(r.urlCheckout)) {
          window.location.assign(r.urlCheckout);
        } else {
          this.redirigiendo.set(false);
          this.avisos.error('La pasarela devolvió una dirección no válida');
        }
      },
      error: () => this.redirigiendo.set(false),
    });
  }
}
