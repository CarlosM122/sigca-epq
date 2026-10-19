import { DatePipe } from '@angular/common';
import { Component, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../core/modelos';
import { FormularioPqr } from '../pqr/formulario-pqr';
import { PqrRadicada, PqrService, RadicarPqrDatos } from '../pqr/pqr.service';
import { MarcoPublico } from '../../shared/marco-publico';
import { Icono } from '../../shared/icono';

/** Radicación sin sesión (P-14). */
@Component({
  selector: 'app-radicar-pqr',
  imports: [FormularioPqr, RouterLink, DatePipe, MarcoPublico, Icono],
  template: `
    <app-marco-publico>
      <section class="tarjeta">
        <span class="icono-cabecera"><app-icono nombre="message-square-text" [tamano]="24" /></span>
        <h1>Radicar una petición, queja, reclamo o recurso</h1>
        @if (resultado(); as r) {
          <div class="exito-caja" role="status">
            <p>Su solicitud quedó radicada con el número <strong>{{ r.numeroRadicado }}</strong>.</p>
            <p>Recibida el {{ r.fechaRegistro | date: 'medium' }}. Fecha límite de respuesta: {{ r.fechaLimiteRespuesta | date: 'longDate' }}.</p>
            <p>Enviamos una confirmación a su correo. Guarde el número para consultar el estado con su identificación.</p>
            <a routerLink="/pqr-publica/consultar">Consultar estado</a>
          </div>
        } @else {
          <p class="ayuda">Si tiene cuenta en el portal, ingrese para radicar sobre sus contratos sin volver a escribir sus datos.</p>
          <app-formulario-pqr modo="publico" [enviando]="enviando()" (radicar)="radicar($event)" />
        }
        <p class="ayuda"><a routerLink="/ingresar">Volver</a></p>
      </section>
    </app-marco-publico>
  `,
})
export class RadicarPqr {
  private readonly servicio = inject(PqrService);
  private readonly formulario = viewChild(FormularioPqr);
  readonly enviando = signal(false);
  readonly resultado = signal<PqrRadicada | null>(null);

  radicar(e: { datos: RadicarPqrDatos; adjuntos: File[] }) {
    this.enviando.set(true);
    this.servicio.radicarPublica(e.datos, e.adjuntos).subscribe({
      next: (r) => this.resultado.set(r),
      error: (err: ApiError) => {
        this.enviando.set(false);
        this.formulario()?.erroresServidor(err);
      },
    });
  }
}
