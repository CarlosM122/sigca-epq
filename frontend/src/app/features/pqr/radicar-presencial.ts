import { DatePipe } from '@angular/common';
import { Component, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../core/modelos';
import { FormularioPqr } from './formulario-pqr';
import { PqrRadicada, PqrService, RadicarPqrDatos } from './pqr.service';

/** Radicación en ventanilla (P-15): CANAL PRESENCIAL, la registra el gestor o el jefe de PQR. */
@Component({
  selector: 'app-radicar-presencial',
  imports: [FormularioPqr, RouterLink, DatePipe],
  template: `
    <p><a routerLink="/pqr">← Volver a la bandeja</a></p>
    <h1>Radicar PQR en ventanilla</h1>
    @if (resultado(); as r) {
      <div class="exito-caja" role="status">
        <p>PQR radicada con el número <strong>{{ r.numeroRadicado }}</strong>.</p>
        <p>Fecha límite de respuesta: {{ r.fechaLimiteRespuesta | date: 'longDate' }}. Entregue el radicado al ciudadano.</p>
        <div class="fila">
          <a class="boton" [routerLink]="['/pqr', r.id]">Ver la PQR</a>
          <button type="button" class="secundario" (click)="resultado.set(null)">Radicar otra</button>
        </div>
      </div>
    } @else {
      <app-formulario-pqr modo="presencial" [enviando]="enviando()" (radicar)="radicar($event)" />
    }
  `,
})
export class RadicarPresencial {
  private readonly servicio = inject(PqrService);
  private readonly formulario = viewChild(FormularioPqr);
  readonly enviando = signal(false);
  readonly resultado = signal<PqrRadicada | null>(null);

  radicar(e: { datos: RadicarPqrDatos; adjuntos: File[] }) {
    this.enviando.set(true);
    this.servicio.radicarPresencial(e.datos, e.adjuntos).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.resultado.set(r);
      },
      error: (err: ApiError) => {
        this.enviando.set(false);
        this.formulario()?.erroresServidor(err);
      },
    });
  }
}
