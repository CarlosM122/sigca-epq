import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { longitudRecortada, sinHtml } from '../../shared/validadores';
import { CAUSAS_ORDEN, ContratoVista, ContratosService, OrdenVista } from './contratos.service';

/**
 * Ficha del contrato: órdenes de suspensión y reconexión (P-11) y cancelación con motivo.
 * El contrato cambia de estado solo al ejecutar una orden.
 */
@Component({
  selector: 'app-detalle-contrato',
  imports: [RouterLink, DatePipe, ReactiveFormsModule, CampoError],
  template: `
    <p><a routerLink="/contratos">← Contratos</a></p>
    @if (contrato(); as c) {
      <h1>Contrato {{ c.numeroContrato }} <span class="estado" [attr.data-estado]="c.estado">{{ c.estado }}</span></h1>
      <dl class="ficha">
        <dt>Titular</dt><dd><a [routerLink]="['/usuarios', c.usuarioId]">{{ c.titular }}</a> ({{ c.codigoUsuario }})</dd>
        <dt>Servicio</dt><dd>{{ c.tipoServicio }}</dd>
        <dt>Predio</dt><dd><a [routerLink]="['/contratos/predios', c.predioId]">{{ c.codigoPredio }}</a> · {{ c.direccion }}, {{ c.municipio }}</dd>
        <dt>Medidor</dt><dd>{{ c.serialMedidor ?? 'Sin medidor' }}</dd>
        <dt>Vinculación</dt><dd>{{ c.fechaVinculacion | date: 'mediumDate' }}</dd>
        @if (c.motivoEstado) { <dt>Último cambio</dt><dd>{{ c.fechaEstado | date: 'short' }} · {{ c.motivoEstado }}</dd> }
      </dl>

      <h2>Órdenes de servicio</h2>
      <table class="tabla">
        <thead><tr><th>Número</th><th>Tipo</th><th>Causa</th><th>Fecha</th><th>Límite</th><th>Estado</th><th>Detalle</th></tr></thead>
        <tbody>
          @for (o of ordenes(); track o.id) {
            <tr [class.alerta]="o.vencida">
              <td>{{ o.numeroOrden }}</td><td>{{ o.tipo }}</td><td>{{ o.causa }}</td>
              <td>{{ o.fechaOrden | date: 'short' }}</td><td>{{ o.fechaLimite ? (o.fechaLimite | date: 'short') : '—' }}</td>
              <td><span class="estado" [attr.data-estado]="o.estado">{{ o.estado }}</span></td>
              <td>{{ o.ejecutadaPor ? 'Ejecutó ' + o.ejecutadaPor : '' }} {{ o.motivoAnulacion ?? o.observacion ?? '' }}</td>
            </tr>
          } @empty { <tr><td colspan="7">Sin órdenes.</td></tr> }
        </tbody>
      </table>
      @if (c.estado === 'SUSPENDIDO' && suspendidoPorMora()) {
        <p class="ayuda">Suspendido por mora: se reconecta automáticamente cuando el usuario paga la deuda o firma un
          acuerdo de pago.</p>
      } @else if (c.estado !== 'CANCELADO' && auth.tienePermiso('ORDEN_GESTIONAR')) {
        <form class="tarjeta" [formGroup]="orden" (ngSubmit)="ordenar(c)" novalidate>
          <h3>{{ c.estado === 'ACTIVO' ? 'Ordenar suspensión' : 'Ordenar reconexión' }}</h3>
          <div class="fila">
            <label>Causa
              <select formControlName="causa">
                @for (k of causas; track k.valor) { <option [value]="k.valor">{{ k.etiqueta }}</option> }
              </select>
            </label>
            <label>Observación <input formControlName="observacion" maxlength="500" />
              <app-campo-error [control]="orden.controls.observacion" /></label>
          </div>
          <p class="ayuda">Las suspensiones por mora las genera Cartera.</p>
          <button type="submit">Crear orden</button>
        </form>
      }

      @if (c.estado !== 'CANCELADO' && auth.tienePermiso('CONTRATO_CANCELAR')) {
        <details class="zona-peligro"><summary>Cancelar contrato</summary>
          <form [formGroup]="cancelacion" (ngSubmit)="cancelar(c)" novalidate>
            <p>Requiere no tener deuda ni órdenes pendientes; el acueducto no se cancela mientras haya alcantarillado vigente.</p>
            <input formControlName="motivo" placeholder="Motivo" maxlength="300" />
            <app-campo-error [control]="cancelacion.controls.motivo" />
            <button type="submit" class="peligro">Cancelar contrato</button>
          </form>
        </details>
      }
    }
  `,
})
export class DetalleContrato {
  private readonly servicio = inject(ContratosService);
  private readonly avisos = inject(AvisosService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);

  readonly id = input.required<string>();
  readonly contrato = signal<ContratoVista | null>(null);
  readonly ordenes = signal<OrdenVista[]>([]);
  readonly causas = CAUSAS_ORDEN;
  /** Igual que el backend: manda la causa de la última suspensión ejecutada. */
  readonly suspendidoPorMora = computed(() => {
    const ultima = this.ordenes()
      .filter((o) => o.tipo === 'SUSPENSION' && o.estado === 'EJECUTADA')
      .sort((a, b) => (b.fechaEjecucion ?? '').localeCompare(a.fechaEjecucion ?? '') || b.id - a.id)[0];
    return ultima?.causa === 'MORA';
  });

  readonly orden = this.fb.group({
    causa: ['SOLICITUD_USUARIO', Validators.required],
    observacion: ['', [Validators.required, longitudRecortada(5, 500), sinHtml]],
  });
  readonly cancelacion = this.fb.group({ motivo: ['', [Validators.required, longitudRecortada(5, 300), sinHtml]] });

  ngOnInit() {
    this.recargar();
  }

  recargar() {
    const id = Number(this.id());
    this.servicio.obtener(id).subscribe((c) => this.contrato.set(c));
    this.servicio.ordenes({ contratoId: id, size: 50 }).subscribe((p) => this.ordenes.set(p.content));
  }

  ordenar(c: ContratoVista) {
    if (!listoParaEnviar(this.orden)) return;
    const v = this.orden.getRawValue();
    const tipo = c.estado === 'ACTIVO' ? 'SUSPENSION' : 'RECONEXION';
    this.servicio.crearOrden({ contratoId: c.id, tipo, causa: v.causa, observacion: v.observacion.trim() }).subscribe({
      next: (o) => {
        this.avisos.exito(`Orden ${o.numeroOrden} creada`);
        this.orden.reset();
        this.recargar();
      },
      error: (e: ApiError) => aplicarErroresServidor(this.orden, e),
    });
  }

  cancelar(c: ContratoVista) {
    if (!listoParaEnviar(this.cancelacion)) return;
    this.servicio.cancelar(c.id, this.cancelacion.getRawValue().motivo.trim(), c.version).subscribe((r) => {
      this.contrato.set(r);
      this.avisos.exito('Contrato cancelado');
    });
  }
}
