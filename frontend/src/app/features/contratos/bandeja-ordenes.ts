import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { CatalogosService } from '../../core/catalogos.service';
import { Pagina } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { listoParaEnviar } from '../../shared/formularios';
import { Paginador } from '../../shared/paginador';
import { longitudRecortada, sinHtml } from '../../shared/validadores';
import { ContratosService, OrdenVista } from './contratos.service';

/** Bandeja de órdenes pendientes (P-11, RN-36): las reconexiones pasadas de su plazo se resaltan. */
@Component({
  selector: 'app-bandeja-ordenes',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, Paginador, CampoError],
  template: `
    <h1>Órdenes de suspensión y reconexión</h1>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <select formControlName="estado">
        <option value="PENDIENTE">Pendientes</option>
        <option value="EJECUTADA">Ejecutadas</option>
        <option value="ANULADA">Anuladas</option>
      </select>
      <select formControlName="tipo">
        <option value="">Suspensión y reconexión</option>
        <option value="SUSPENSION">Suspensión</option>
        <option value="RECONEXION">Reconexión</option>
      </select>
      <select formControlName="municipioId">
        <option [ngValue]="null">Todos los municipios</option>
        @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
      </select>
      <label><input type="checkbox" formControlName="vencidas" /> Solo reconexiones vencidas</label>
      <button type="submit">Filtrar</button>
    </form>
    <table class="tabla">
      <thead>
        <tr><th>Orden</th><th>Tipo</th><th>Causa</th><th>Contrato</th><th>Titular y dirección</th><th>Fecha</th><th>Límite</th><th>Estado</th><th></th></tr>
      </thead>
      <tbody>
        @for (o of pagina()?.content ?? []; track o.id) {
          <tr [class.alerta]="o.vencida">
            <td>{{ o.numeroOrden }}</td>
            <td>{{ o.tipo }} @if (o.vencida) { <span class="etiqueta peligro">Vencida</span> }</td>
            <td>{{ o.causa }}</td>
            <td><a [routerLink]="['/contratos', o.contratoId]">{{ o.numeroContrato }}</a> · {{ o.tipoServicio }}</td>
            <td>{{ o.titular }}<br /><small>{{ o.direccion }}, {{ o.municipio }}</small></td>
            <td>{{ o.fechaOrden | date: 'short' }}</td>
            <td>{{ o.fechaLimite ? (o.fechaLimite | date: 'short') : '—' }}</td>
            <td><span class="estado" [attr.data-estado]="o.estado">{{ o.estado }}</span></td>
            <td>
              @if (o.estado === 'PENDIENTE' && auth.tienePermiso('ORDEN_GESTIONAR')) {
                <button type="button" class="enlace" (click)="abrir(o, 'ejecutar')">Ejecutar</button>
                <button type="button" class="enlace" (click)="abrir(o, 'anular')">Anular</button>
              }
            </td>
          </tr>
        } @empty { <tr><td colspan="9">No hay órdenes con esos filtros.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />

    @if (seleccion(); as s) {
      <form class="tarjeta" [formGroup]="accion" (ngSubmit)="confirmar()" novalidate>
        <h3>{{ s.accion === 'ejecutar' ? 'Ejecutar' : 'Anular' }} la orden {{ s.orden.numeroOrden }}</h3>
        <label>{{ s.accion === 'ejecutar' ? 'Observación (opcional)' : 'Motivo de la anulación' }}
          <input formControlName="texto" maxlength="500" />
        </label>
        <app-campo-error [control]="accion.controls.texto" />
        @if (s.accion === 'ejecutar') {
          <p class="ayuda">Al ejecutarla el contrato queda {{ s.orden.tipo === 'SUSPENSION' ? 'SUSPENDIDO' : 'ACTIVO' }}. Solo un asesor o administrador puede ejecutarla.</p>
        }
        <div class="acciones">
          <button type="submit" [class.peligro]="s.accion === 'anular'">Confirmar</button>
          <button type="button" class="secundario" (click)="seleccion.set(null)">Cerrar</button>
        </div>
      </form>
    }
  `,
})
export class BandejaOrdenes {
  private readonly servicio = inject(ContratosService);
  private readonly avisos = inject(AvisosService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly pagina = signal<Pagina<OrdenVista> | null>(null);
  readonly seleccion = signal<{ orden: OrdenVista; accion: 'ejecutar' | 'anular' } | null>(null);

  readonly filtros = this.fb.group({
    estado: ['PENDIENTE'],
    tipo: [''],
    municipioId: this.fb.control<number | null>(this.auth.sesion()?.municipioId ?? null),
    vencidas: [false],
  });
  readonly accion = this.fb.group({ texto: ['', [Validators.maxLength(500), sinHtml]] });

  constructor() {
    this.buscar(0);
  }

  buscar(page: number) {
    const v = this.filtros.getRawValue();
    this.servicio.ordenes({ ...v, vencidas: v.vencidas || undefined, page }).subscribe((p) => this.pagina.set(p));
  }

  abrir(orden: OrdenVista, accion: 'ejecutar' | 'anular') {
    const texto = this.accion.controls.texto;
    texto.setValidators(accion === 'anular'
      ? [Validators.required, longitudRecortada(5, 300), sinHtml]
      : [Validators.maxLength(500), sinHtml]);
    texto.reset('');
    this.seleccion.set({ orden, accion });
  }

  confirmar() {
    const s = this.seleccion();
    if (!s || !listoParaEnviar(this.accion)) return;
    const texto = this.accion.getRawValue().texto.trim();
    const op = s.accion === 'ejecutar'
      ? this.servicio.ejecutarOrden(s.orden.id, texto || null, s.orden.version)
      : this.servicio.anularOrden(s.orden.id, texto, s.orden.version);
    op.subscribe((o) => {
      this.avisos.exito(`Orden ${o.numeroOrden} ${o.estado.toLowerCase()}`);
      this.seleccion.set(null);
      this.buscar(this.pagina()?.page.number ?? 0);
    });
  }
}
