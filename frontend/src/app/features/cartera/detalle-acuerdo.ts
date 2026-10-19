import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { longitudRecortada, sinHtml } from '../../shared/validadores';
import { AcuerdoDetalle, CarteraService } from './cartera.service';

@Component({
  selector: 'app-detalle-acuerdo',
  imports: [ReactiveFormsModule, CampoError, RouterLink, CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    <p><a routerLink="/cartera/acuerdos">← Acuerdos</a></p>
    @if (detalle(); as d) {
      <div class="encabezado">
        <h1>Acuerdo {{ d.acuerdo.numeroAcuerdo }}</h1>
        <span class="estado" [attr.data-estado]="d.acuerdo.estado">{{ d.acuerdo.estado }}</span>
      </div>
      <dl class="ficha">
        <dt>Usuario</dt><dd>{{ d.acuerdo.codigoUsuario }} · {{ d.acuerdo.titular }}</dd>
        <dt>Fecha</dt><dd>{{ d.acuerdo.fechaAcuerdo | date: 'longDate' }} · {{ d.acuerdo.funcionario }}</dd>
        <dt>Deuda</dt><dd>{{ d.acuerdo.totalDeuda | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
        <dt>Cuota inicial</dt><dd>{{ d.acuerdo.cuotaInicial | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
        <dt>Tasa</dt><dd>{{ d.acuerdo.tasaInteresAnual | number: '1.0-4' }} % E.A.</dd>
        <dt>Saldo por pagar</dt><dd>{{ d.acuerdo.saldoPorPagar | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
        @if (d.acuerdo.motivoEstado) { <dt>Motivo</dt><dd>{{ d.acuerdo.motivoEstado }}</dd> }
      </dl>
      <h2>Facturas incluidas</h2>
      <table class="tabla">
        <thead><tr><th>Factura</th><th>Periodo</th><th>Valor incluido</th><th>Estado</th></tr></thead>
        <tbody>
          @for (f of d.facturas; track f.facturaId) {
            <tr><td>{{ f.numeroFactura }}</td><td>{{ f.periodo }}</td>
              <td class="num">{{ f.valorIncluido | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td><td>{{ f.estadoFactura }}</td></tr>
          }
        </tbody>
      </table>
      <h2>Cuotas</h2>
      <table class="tabla">
        <thead><tr><th>Cuota</th><th>Vence</th><th>Capital</th><th>Interés</th><th>Valor</th><th>Estado</th></tr></thead>
        <tbody>
          @for (c of d.cuotas; track c.id) {
            <tr><td>{{ c.numeroCuota === 0 ? 'Inicial' : c.numeroCuota }}</td><td>{{ c.fechaVencimiento | date: 'mediumDate' }}</td>
              <td class="num">{{ c.valorCapital | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
              <td class="num">{{ c.valorInteres | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
              <td class="num">{{ c.valorCuota | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
              <td><span class="estado" [attr.data-estado]="c.estado">{{ c.estado }}</span>
                @if (c.fechaPago) { <small>{{ c.fechaPago | date: 'shortDate' }}</small> }</td></tr>
          }
        </tbody>
      </table>
      @if (d.acuerdo.estado === 'VIGENTE' && auth.tienePermiso('ACUERDO_GESTIONAR')) {
        <form class="tarjeta" [formGroup]="anulacion" (ngSubmit)="anular()" novalidate>
          <h2>Anular el acuerdo</h2>
          <p class="ayuda">Las facturas vuelven a VENCIDA y el cobro persuasivo se reactiva.</p>
          <textarea formControlName="motivo" rows="3" maxlength="300" placeholder="Motivo (10 a 300 caracteres)" aria-label="Motivo"></textarea>
          <app-campo-error [control]="anulacion.controls.motivo" />
          <button type="submit" class="peligro">Anular</button>
        </form>
      }
    }
  `,
})
export class DetalleAcuerdo implements OnInit {
  private readonly servicio = inject(CarteraService);
  private readonly avisos = inject(AvisosService);
  readonly auth = inject(AuthService);
  readonly id = input.required<string>();
  readonly detalle = signal<AcuerdoDetalle | null>(null);
  readonly anulacion = inject(NonNullableFormBuilder).group({
    motivo: ['', [Validators.required, longitudRecortada(10, 300), sinHtml]],
  });

  ngOnInit() {
    this.servicio.acuerdo(Number(this.id())).subscribe((d) => this.detalle.set(d));
  }

  anular() {
    if (!listoParaEnviar(this.anulacion) || !window.confirm('¿Anular el acuerdo de pago?')) return;
    this.servicio.anular(Number(this.id()), this.anulacion.getRawValue().motivo.trim()).subscribe({
      next: (d) => {
        this.detalle.set(d);
        this.avisos.exito('Acuerdo anulado');
      },
      error: (e: ApiError) => aplicarErroresServidor(this.anulacion, e),
    });
  }
}
