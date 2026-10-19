import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Pagina } from '../../core/modelos';
import { Paginador } from '../../shared/paginador';
import { AcuerdoVista, CarteraService } from './cartera.service';

/** Acuerdos de pago con su avance (P-12). */
@Component({
  selector: 'app-acuerdos',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, Paginador],
  template: `
    <p><a routerLink="/cartera">← Cartera</a></p>
    <h1>Acuerdos de pago</h1>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <input formControlName="texto" placeholder="AP-AAAA-000000 o código de usuario" maxlength="20" aria-label="Número o usuario" />
      <select formControlName="estado" aria-label="Estado">
        <option value="">Todos los estados</option>
        @for (e of estados; track e) { <option [value]="e">{{ e }}</option> }
      </select>
      <button type="submit">Buscar</button>
    </form>
    <table class="tabla">
      <thead><tr><th>Acuerdo</th><th>Usuario</th><th>Fecha</th><th>Deuda</th><th>Cuotas</th><th>Próximo vencimiento</th><th>Saldo</th><th>Estado</th></tr></thead>
      <tbody>
        @for (a of pagina()?.content ?? []; track a.id) {
          <tr>
            <td><a [routerLink]="[a.id]">{{ a.numeroAcuerdo }}</a></td>
            <td>{{ a.codigoUsuario }}<br /><small>{{ a.titular }}</small></td>
            <td>{{ a.fechaAcuerdo | date: 'mediumDate' }}</td>
            <td class="num">{{ a.totalDeuda | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
            <td>{{ a.cuotasPagadas }} / {{ a.numeroCuotas + 1 }} pagadas
              @if (a.cuotasVencidas) { <span class="etiqueta peligro">{{ a.cuotasVencidas }} vencida(s)</span> }</td>
            <td>{{ a.proximoVencimiento ? (a.proximoVencimiento | date: 'mediumDate') : '—' }}</td>
            <td class="num">{{ a.saldoPorPagar | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
            <td><span class="estado" [attr.data-estado]="a.estado">{{ a.estado }}</span></td>
          </tr>
        } @empty { <tr><td colspan="8">Sin acuerdos con esos filtros.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />
  `,
})
export class Acuerdos {
  private readonly servicio = inject(CarteraService);
  readonly estados = ['VIGENTE', 'CUMPLIDO', 'INCUMPLIDO', 'ANULADO'];
  readonly pagina = signal<Pagina<AcuerdoVista> | null>(null);
  readonly filtros = inject(NonNullableFormBuilder).group({ texto: [''], estado: [''] });

  constructor() {
    this.buscar(0);
  }

  buscar(page: number) {
    const v = this.filtros.getRawValue();
    this.servicio.acuerdos({ estado: v.estado, texto: v.texto.trim(), page }).subscribe((p) => this.pagina.set(p));
  }
}
