import { CurrencyPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { EstadoTransaccion, PagosService } from './pagos.service';
import { MarcoPublico } from '../../shared/marco-publico';
import { Icono } from '../../shared/icono';

/**
 * Página de retorno de la pasarela. El estado mostrado es el que el backend recibió por el webhook firmado, nunca
 * los parámetros de la URL; mientras la pasarela no confirme, se consulta cada 3 s (hasta 1 minuto).
 */
@Component({
  selector: 'app-resultado-pago',
  imports: [RouterLink, CurrencyPipe, MarcoPublico, Icono],
  template: `
    <app-marco-publico>
      <section class="tarjeta angosta">
        <span class="icono-cabecera"><app-icono nombre="credit-card" [tamano]="24" /></span>
        <h1>Resultado del pago</h1>
        @if (estado(); as e) {
          <p>Referencia de pago {{ e.referenciaPago }} · {{ e.monto | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
          @switch (e.estado) {
            @case ('APROBADA') {
              <div class="exito-caja" role="status">Pago aprobado. Comprobante {{ e.codigoPago }}: lo enviamos a su correo y puede descargarlo en Mis facturas.</div>
            }
            @case ('INICIADA') {
              <p role="status">{{ agotado() ? 'La pasarela aún no confirma el pago. Revise Mis facturas en unos minutos.' : 'Esperando la confirmación de la pasarela…' }}</p>
            }
            @case ('EXPIRADA') { <p class="error">El intento de pago expiró sin respuesta de la pasarela. Puede intentarlo de nuevo.</p> }
            @default { <p class="error" role="alert">El pago no se completó ({{ e.estado }}). {{ e.mensaje }}</p> }
          }
        } @else if (invalida()) {
          <p class="error">La referencia del pago no es válida.</p>
        } @else {
          <p class="cargando">Consultando…</p>
        }
        <p><a routerLink="/portal/facturas">Volver a mis facturas</a></p>
      </section>
    </app-marco-publico>
  `,
})
export class ResultadoPago implements OnInit, OnDestroy {
  private readonly servicio = inject(PagosService);
  readonly referencia = input<string>();
  readonly estado = signal<EstadoTransaccion | null>(null);
  readonly invalida = signal(false);
  readonly agotado = signal(false);
  private intentos = 0;
  private temporizador?: ReturnType<typeof setTimeout>;

  ngOnInit() {
    const ref = this.referencia();
    if (!ref || !/^TX-[0-9A-F]{32}$/.test(ref)) {
      this.invalida.set(true);
      return;
    }
    this.consultar(ref);
  }

  ngOnDestroy() {
    clearTimeout(this.temporizador);
  }

  private consultar(ref: string) {
    this.servicio.estadoTransaccion(ref).subscribe({
      next: (e) => {
        this.estado.set(e);
        if (e.estado === 'INICIADA') {
          if (++this.intentos < 20) {
            this.temporizador = setTimeout(() => this.consultar(ref), 3000);
          } else {
            this.agotado.set(true);
          }
        }
      },
      error: () => this.invalida.set(true),
    });
  }
}
