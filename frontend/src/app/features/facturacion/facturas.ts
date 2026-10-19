import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError, Pagina } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { Paginador } from '../../shared/paginador';
import { longitudRecortada, periodo, sinHtml } from '../../shared/validadores';
import { FacturaDetalle, FacturaResumen, FacturacionService, descargarBlob } from './facturacion.service';

/** RF-07, P-10: búsqueda de facturas, detalle con líneas, PDF, anulación con motivo y reexpedición. */
@Component({
  selector: 'app-facturas',
  imports: [ReactiveFormsModule, CampoError, Paginador, CurrencyPipe, DatePipe, DecimalPipe, RouterLink],
  template: `
    <div class="encabezado">
      <h1>Facturas</h1>
      @if (auth.tienePermiso('FACTURA_GENERAR_LOTE')) {
        <a class="boton" routerLink="/facturacion/lotes">Lotes de facturación</a>
      }
    </div>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)" novalidate>
      <input formControlName="numero" placeholder="FAC-AAAAMM-NNNNNNN" maxlength="18" aria-label="Número de factura" />
      <input formControlName="usuario" placeholder="USR-NNNNNNNN" maxlength="12" aria-label="Código de usuario" />
      <input formControlName="predio" placeholder="PRD-NNNNNNNN" maxlength="12" aria-label="Código de predio" />
      <input formControlName="periodo" placeholder="Periodo AAAAMM" maxlength="6" aria-label="Periodo" />
      <select formControlName="estado" aria-label="Estado">
        <option value="">Todos los estados</option>
        @for (e of estados; track e) { <option [value]="e">{{ e }}</option> }
      </select>
      <button type="submit">Buscar</button>
    </form>
    <app-campo-error [control]="filtros.controls.numero" />
    <app-campo-error [control]="filtros.controls.usuario" />
    <app-campo-error [control]="filtros.controls.predio" />
    <app-campo-error [control]="filtros.controls.periodo" />

    <table class="tabla">
      <thead><tr><th>Número</th><th>Periodo</th><th>Titular</th><th>Predio</th><th>Vence</th><th>Total</th><th>Exigible</th><th>Estado</th><th></th></tr></thead>
      <tbody>
        @for (f of pagina()?.content ?? []; track f.id) {
          <tr>
            <td>{{ f.numeroFactura }}</td><td>{{ f.periodo }}</td><td>{{ f.titular }} <small class="ayuda">{{ f.codigoUsuario }}</small></td>
            <td>{{ f.codigoPredio }} <small class="ayuda">{{ f.municipio }}</small></td>
            <td>{{ f.fechaVencimiento | date: 'mediumDate' }}</td>
            <td class="num">{{ f.totalAPagar | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
            <td class="num">{{ f.valorExigible | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}
              @if (f.valorEnReclamo > 0) { <span class="etiqueta">En reclamo</span> }</td>
            <td><span class="estado" [attr.data-estado]="f.estado">{{ f.estado }}</span></td>
            <td>
              <button type="button" class="enlace" (click)="ver(f.id)">Ver</button>
              <button type="button" class="enlace" (click)="pdf(f.id, f.numeroFactura)">PDF</button>
            </td>
          </tr>
        } @empty { <tr><td colspan="9">Sin facturas para los filtros dados.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />

    @if (detalle(); as f) {
      <section class="tarjeta" aria-live="polite">
        <div class="encabezado">
          <h2>Factura {{ f.numeroFactura }} <span class="estado" [attr.data-estado]="f.estado">{{ f.estado }}</span></h2>
          <button type="button" class="secundario" (click)="cerrar()">Cerrar</button>
        </div>
        <dl class="ficha">
          <dt>Titular</dt><dd>{{ f.titular }} ({{ f.codigoUsuario }})</dd>
          <dt>Predio</dt><dd>{{ f.codigoPredio }} · {{ f.direccion }}, {{ f.municipio }} · {{ f.uso }}@if (f.estrato) { estrato {{ f.estrato }} }</dd>
          <dt>Periodo</dt><dd>{{ f.periodo }}</dd>
          <dt>Emisión / vencimiento</dt><dd>{{ f.fechaEmision | date: 'mediumDate' }} / {{ f.fechaVencimiento | date: 'mediumDate' }}</dd>
          <dt>Referencia de pago</dt><dd>{{ f.referenciaPago }}</dd>
          @if (f.reexpedicionDe) { <dt>Reexpide a</dt><dd>{{ f.reexpedicionDe }}</dd> }
          @if (f.reexpedidaComo) { <dt>Reexpedida como</dt><dd>{{ f.reexpedidaComo }}</dd> }
          @if (f.motivoAnulacion) { <dt>Motivo de anulación</dt><dd class="texto-largo">{{ f.motivoAnulacion }}</dd> }
          @if (f.loteId) { <dt>Lote</dt><dd>#{{ f.loteId }}</dd> }
          <dt>Sincronización ERP</dt><dd>{{ f.estadoSyncErp }}</dd>
        </dl>
        @if (f.consumoEstimado) {
          <p class="aviso-inline">Incluye consumo estimado por promedio (RN-35).</p>
        }
        <table class="tabla">
          <thead><tr><th>Contrato</th><th>Servicio</th><th>Lectura</th><th>Consumo m³</th><th>Cargo fijo</th><th>Consumo</th><th>Subsidio / contribución</th><th>Total</th><th></th></tr></thead>
          <tbody>
            @for (l of f.lineas; track l.id) {
              <tr>
                <td>{{ l.numeroContrato }}</td><td>{{ l.tipoServicio }}</td>
                <td>@if (l.lecturaActual !== null && l.lecturaActual !== undefined) { {{ l.lecturaAnterior | number: '1.0-3' }} → {{ l.lecturaActual | number: '1.0-3' }} } @else { — }</td>
                <td class="num">{{ l.consumo | number: '1.0-3' }}@if (l.consumoEstimado) { <span class="etiqueta">Estimado</span> }</td>
                <td class="num">{{ l.cargoFijo | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                <td class="num">{{ l.valorConsumo | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                <td class="num">{{ l.subsidio + l.contribucion | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                <td class="num">{{ l.totalLinea | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                <td>@if (l.enReclamo) { <span class="etiqueta">En reclamo</span> }</td>
              </tr>
            }
          </tbody>
        </table>
        <p>Total facturado: {{ f.totalAPagar | currency: 'COP' : 'symbol-narrow' : '1.0-2' }} · Pagado: {{ f.valorPagado | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}
          @if (f.valorAjustes) { · Ajustes: {{ f.valorAjustes | currency: 'COP' : 'symbol-narrow' : '1.0-2' }} }
          · En reclamo: {{ f.valorEnReclamo | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</p>
        <p class="total">Valor exigible: {{ f.valorExigible | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</p>
        <div class="acciones">
          <button type="button" class="secundario" (click)="pdf(f.id, f.numeroFactura)">Descargar PDF</button>
          @if (auth.tienePermiso('FACTURA_ANULAR') && (f.estado === 'PENDIENTE' || f.estado === 'VENCIDA') && !f.valorPagado && !f.valorAjustes) {
            <button type="button" class="peligro" (click)="anulando.set(true)">Anular</button>
          }
          @if (auth.tienePermiso('FACTURA_ANULAR') && f.estado === 'ANULADA' && !f.reexpedidaComo) {
            <button type="button" (click)="reexpedir(f)" [disabled]="enviando()">Reexpedir</button>
          }
        </div>

        @if (anulando()) {
          <form [formGroup]="anulacion" (ngSubmit)="anular(f)" novalidate>
            <h3>Anular {{ f.numeroFactura }}</h3>
            <label>Motivo (10 a 500 caracteres)
              <textarea formControlName="motivo" rows="3" maxlength="500"></textarea>
            </label>
            <app-campo-error [control]="anulacion.controls.motivo" />
            <label class="check"><input type="checkbox" formControlName="reexpedir" /> Reexpedir de inmediato la factura del mismo periodo</label>
            <div class="acciones">
              <button type="submit" class="peligro" [disabled]="enviando()">Confirmar anulación</button>
              <button type="button" class="secundario" (click)="anulando.set(false)">Cancelar</button>
            </div>
          </form>
        }
      </section>
    }
  `,
})
export class Facturas {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(FacturacionService);
  private readonly avisos = inject(AvisosService);
  readonly auth = inject(AuthService);
  readonly estados = ['PENDIENTE', 'VENCIDA', 'EN_ACUERDO', 'PAGADA', 'ANULADA'];
  readonly pagina = signal<Pagina<FacturaResumen> | null>(null);
  readonly detalle = signal<FacturaDetalle | null>(null);
  readonly anulando = signal(false);
  readonly enviando = signal(false);
  private paginaActual = 0;

  readonly filtros = this.fb.group({
    numero: ['', Validators.pattern(/^FAC-\d{6}-\d{7}$/i)],
    usuario: ['', Validators.pattern(/^USR-\d{8}$/i)],
    predio: ['', Validators.pattern(/^PRD-\d{8}$/i)],
    periodo: ['', periodo],
    estado: [''],
  });
  readonly anulacion = this.fb.group({
    motivo: ['', [Validators.required, longitudRecortada(10, 500), sinHtml]],
    reexpedir: [true],
  });

  constructor() {
    this.buscar(0);
  }

  buscar(page: number) {
    if (!listoParaEnviar(this.filtros)) return;
    this.paginaActual = page;
    const v = this.filtros.getRawValue();
    this.servicio
      .facturas({
        numero: v.numero.trim().toUpperCase(),
        usuario: v.usuario.trim().toUpperCase(),
        predio: v.predio.trim().toUpperCase(),
        periodo: v.periodo ? Number(v.periodo) : null,
        estado: v.estado,
        page,
      })
      .subscribe((p) => this.pagina.set(p));
  }

  ver(id: number) {
    this.anulando.set(false);
    this.servicio.factura(id).subscribe((f) => this.detalle.set(f));
  }

  cerrar() {
    this.detalle.set(null);
    this.anulando.set(false);
  }

  anular(f: FacturaDetalle) {
    if (!listoParaEnviar(this.anulacion)) return;
    const v = this.anulacion.getRawValue();
    this.enviando.set(true);
    this.servicio.anular(f.id, v.motivo.trim(), v.reexpedir, f.version).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.anulando.set(false);
        this.anulacion.reset();
        this.avisos.exito(
          r.reexpedida
            ? `Factura ${f.numeroFactura} anulada y reexpedida como ${r.reexpedida.numeroFactura}`
            : `Factura ${f.numeroFactura} anulada`,
        );
        this.detalle.set(r.reexpedida ?? r.anulada);
        this.buscar(this.paginaActual);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        aplicarErroresServidor(this.anulacion, e);
      },
    });
  }

  reexpedir(f: FacturaDetalle) {
    this.enviando.set(true);
    this.servicio.reexpedir(f.id).subscribe({
      next: (n) => {
        this.enviando.set(false);
        this.avisos.exito(`Factura reexpedida como ${n.numeroFactura}`);
        this.detalle.set(n);
        this.buscar(this.paginaActual);
      },
      error: () => this.enviando.set(false),
    });
  }

  pdf(id: number, numero: string) {
    this.servicio.pdf(id).subscribe((b) => descargarBlob(b, `${numero}.pdf`));
  }
}
