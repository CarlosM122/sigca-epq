import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { descargarBlob } from '../facturacion/facturacion.service';
import { Obligacion, PATRON_REFERENCIA, PagoVista, PagosService } from './pagos.service';

/**
 * Pago presencial o por recaudo bancario (RF-06, P-23) de una factura o de la siguiente cuota de un acuerdo. Se
 * cobra exactamente el valor exigible (P-05): el saldo menos lo que está en reclamo (RN-33).
 */
@Component({
  selector: 'app-registrar-pago',
  imports: [ReactiveFormsModule, CampoError, CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    <h1>Registrar pago</h1>
    <form class="filtros" [formGroup]="busqueda" (ngSubmit)="buscar()" novalidate>
      <input formControlName="referencia" placeholder="FAC-AAAAMM-NNNNNNN o AP-AAAA-NNNNNN[-NN]" maxlength="20"
        aria-label="Referencia de pago" />
      <button type="submit">Buscar</button>
    </form>
    <app-campo-error [control]="busqueda.controls.referencia" />
    <p class="ayuda">Con el número de acuerdo sin cuota se cobra la siguiente cuota pendiente.</p>

    @if (obligacion(); as o) {
      <section class="tarjeta">
        <h2>{{ o.tipo === 'FACTURA' ? 'Factura' : 'Cuota' }} {{ o.referencia }}</h2>
        <dl class="ficha">
          <dt>Titular</dt><dd>{{ o.titular }} ({{ o.codigoUsuario }})</dd>
          <dt>Estado</dt><dd><span class="estado" [attr.data-estado]="o.estado">{{ o.estado }}</span></dd>
          @if (o.fechaVencimiento) { <dt>Vence</dt><dd>{{ o.fechaVencimiento | date: 'mediumDate' }}</dd> }
          <dt>Total</dt><dd>{{ o.totalAPagar | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
          <dt>En reclamo</dt><dd>{{ o.valorEnReclamo | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
        </dl>
        @if (o.lineas?.length) {
          <table class="tabla">
            <thead><tr><th>Contrato</th><th>Servicio</th><th>Consumo m³</th><th>Total</th><th></th></tr></thead>
            <tbody>
              @for (l of o.lineas; track l.id) {
                <tr><td>{{ l.numeroContrato }}</td><td>{{ l.tipoServicio }}</td><td class="num">{{ l.consumo | number: '1.0-3' }}</td>
                  <td class="num">{{ l.totalLinea | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                  <td>@if (l.enReclamo) { <span class="etiqueta">En reclamo</span> }</td></tr>
              }
            </tbody>
          </table>
        }
        <p class="total">Valor a cobrar: {{ o.valorExigible | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</p>
        @if (o.valorEnReclamo > 0) {
          <p class="aviso-inline">Hay {{ o.valorEnReclamo | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} en reclamo: no se cobran hasta que se resuelva (RN-33).</p>
        }

        @if (o.admitePago) {
          <form [formGroup]="pago" (ngSubmit)="registrar(o)" novalidate>
            <label>Canal
              <select formControlName="canal">
                <option value="PRESENCIAL">Caja (presencial)</option>
                <option value="BANCO">Recaudo bancario</option>
              </select>
            </label>
            @if (pago.controls.canal.value === 'BANCO') {
              <label>Referencia del recaudo <input formControlName="referenciaBanco" maxlength="60" /></label>
              <app-campo-error [control]="pago.controls.referenciaBanco" />
            }
            <button type="submit" [disabled]="enviando()">Registrar pago de {{ o.valorExigible | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</button>
          </form>
        } @else {
          <p class="aviso-inline peligro">{{ o.motivoNoAdmite ?? 'Esta obligación no admite pagos en su estado actual.' }}</p>
        }
      </section>
    }
    @if (comprobante(); as c) {
      <div class="exito-caja" role="status">
        Pago {{ c.codigoPago }} de {{ c.valor | currency: 'COP' : 'symbol-narrow' : '1.0-2' }} registrado para {{ c.referencia }}.
        Estado: {{ c.estadoObligacion }}.
        @if (c.comprobanteEnviado) { El comprobante se envió al correo del titular. }
        <button type="button" class="enlace" (click)="descargar(c)">Descargar comprobante</button>
      </div>
    }
  `,
})
export class RegistrarPago {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(PagosService);
  private readonly avisos = inject(AvisosService);
  readonly obligacion = signal<Obligacion | null>(null);
  readonly comprobante = signal<PagoVista | null>(null);
  readonly enviando = signal(false);

  readonly busqueda = this.fb.group({
    referencia: ['', [Validators.required, Validators.pattern(PATRON_REFERENCIA)]],
  });

  readonly pago = this.fb.group({
    canal: ['PRESENCIAL' as 'PRESENCIAL' | 'BANCO'],
    referenciaBanco: [''],
  });

  constructor() {
    this.pago.controls.canal.valueChanges.subscribe((c) => {
      const ref = this.pago.controls.referenciaBanco;
      ref.setValidators(c === 'BANCO' ? [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{4,60}$/)] : []);
      ref.updateValueAndValidity();
    });
    this.busqueda.controls.referencia.valueChanges.subscribe((v) => {
      const limpio = v.trim().toUpperCase();
      if (v !== limpio) this.busqueda.controls.referencia.setValue(limpio, { emitEvent: false });
    });
  }

  buscar() {
    if (!listoParaEnviar(this.busqueda)) return;
    this.obligacion.set(null);
    this.comprobante.set(null);
    this.servicio.buscarObligacion(this.busqueda.getRawValue().referencia).subscribe((o) => this.obligacion.set(o));
  }

  registrar(o: Obligacion) {
    if (!listoParaEnviar(this.pago)) return;
    const v = this.pago.getRawValue();
    this.enviando.set(true);
    this.servicio
      .registrar({
        referencia: o.referencia,
        canal: v.canal,
        valor: o.valorExigible,
        referenciaBanco: v.canal === 'BANCO' ? v.referenciaBanco.trim() : null,
      })
      .subscribe({
        next: (c) => {
          this.enviando.set(false);
          this.comprobante.set(c);
          this.obligacion.set(null);
          this.pago.reset();
          this.avisos.exito(`Pago ${c.codigoPago} registrado`);
        },
        error: (e: ApiError) => {
          this.enviando.set(false);
          aplicarErroresServidor(this.pago, e);
        },
      });
  }

  descargar(c: PagoVista) {
    this.servicio.comprobante(c.id).subscribe((b) => descargarBlob(b, `${c.codigoPago}.pdf`));
  }
}
