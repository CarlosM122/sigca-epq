import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { TIPOS_IDENTIFICACION } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { listoParaEnviar } from '../../shared/formularios';
import { documentoValido } from '../../shared/validadores';
import { RADICADO } from '../pqr/formulario-pqr';
import { ConsultaPqr, PqrService } from '../pqr/pqr.service';
import { MarcoPublico } from '../../shared/marco-publico';
import { Icono } from '../../shared/icono';

/** Consulta sin sesión por radicado e identificación del solicitante o del titular (RF-10, RN-24). */
@Component({
  selector: 'app-consultar-pqr',
  imports: [ReactiveFormsModule, CampoError, RouterLink, DatePipe, MarcoPublico, Icono],
  template: `
    <app-marco-publico>
      <section class="tarjeta angosta">
        <span class="icono-cabecera"><app-icono nombre="file-search" [tamano]="24" /></span>
        <h1>Consultar mi PQR</h1>
        <form [formGroup]="form" (ngSubmit)="consultar()" novalidate>
          <label>Número de radicado
            <input formControlName="radicado" placeholder="AAAAMM-NNNN" maxlength="11" />
          </label>
          <app-campo-error [control]="form.controls.radicado" />
          <label>Tipo de identificación
            <select formControlName="tipoIdentificacion">
              @for (t of tiposId; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
            </select>
          </label>
          <label>Número de identificación (del solicitante o del titular del contrato)
            <input formControlName="numeroIdentificacion" maxlength="15" autocomplete="off" />
          </label>
          <app-campo-error [control]="form.controls.numeroIdentificacion" />
          <button type="submit" class="bloque" [disabled]="consultando()">Consultar</button>
        </form>
        @if (noEncontrada()) {
          <p class="aviso-inline" role="status">No encontramos una PQR con ese radicado para esa identificación.</p>
        }
        @if (pqr(); as r) {
          <dl class="ficha">
            <dt>Radicado</dt><dd>{{ r.numeroRadicado }}</dd>
            <dt>Tipo</dt><dd>{{ r.tipo }}</dd>
            <dt>Estado</dt><dd><span class="estado" [attr.data-estado]="r.estado">{{ r.estado }}</span>
              @if (r.silencioPositivo) { <span class="etiqueta peligro">Silencio administrativo positivo</span> }</dd>
            <dt>Recibida</dt><dd>{{ r.fechaRegistro | date: 'medium' }}</dd>
            <dt>Fecha límite</dt><dd>{{ r.fechaLimiteRespuesta | date: 'longDate' }}</dd>
            @if (r.respuesta) {
              <dt>Respuesta</dt><dd class="texto-largo">{{ r.respuesta }}</dd>
              <dt>Fecha de respuesta</dt><dd>{{ r.fechaRespuesta | date: 'medium' }}</dd>
            }
            @if (r.decisionRecurso) { <dt>Decisión del recurso</dt><dd>{{ r.decisionRecurso }}</dd> }
          </dl>
        }
        <p class="ayuda"><a routerLink="/pqr-publica/radicar">Radicar una solicitud</a> · <a routerLink="/ingresar">Volver</a></p>
      </section>
    </app-marco-publico>
  `,
})
export class ConsultarPqr {
  private readonly servicio = inject(PqrService);
  readonly tiposId = TIPOS_IDENTIFICACION;
  readonly pqr = signal<ConsultaPqr | null>(null);
  readonly noEncontrada = signal(false);
  readonly consultando = signal(false);

  readonly form = inject(NonNullableFormBuilder).group(
    {
      radicado: ['', [Validators.required, Validators.pattern(RADICADO)]],
      tipoIdentificacion: ['CC', Validators.required],
      numeroIdentificacion: ['', Validators.required],
    },
    { validators: documentoValido() },
  );

  consultar() {
    if (!listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    this.pqr.set(null);
    this.noEncontrada.set(false);
    this.consultando.set(true);
    this.servicio.consultarPublica(v.radicado.trim(), v.tipoIdentificacion, v.numeroIdentificacion.trim().toUpperCase()).subscribe({
      next: (r) => {
        this.consultando.set(false);
        this.pqr.set(r);
      },
      error: (e) => {
        this.consultando.set(false);
        this.noEncontrada.set(e?.status === 404);
      },
    });
  }
}
