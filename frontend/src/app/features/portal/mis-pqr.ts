import { DatePipe } from '@angular/common';
import { Component, inject, signal, viewChild } from '@angular/core';

import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { FormularioPqr } from '../pqr/formulario-pqr';
import { ContextoPortal, PqrPortal, PqrService, RadicarPqrDatos, guardarBlob } from '../pqr/pqr.service';

/**
 * PQR del ciudadano en el portal (RF-24, P-14 canal PORTAL): lista de sus PQR con la respuesta, radicación sobre
 * sus contratos y recurso de reposición (con apelación opcional) contra una respuesta dentro del plazo (P-15).
 */
@Component({
  selector: 'app-mis-pqr',
  imports: [DatePipe, FormularioPqr],
  template: `
    <div class="encabezado">
      <h1>Mis PQR</h1>
      @if (!formulario()) {
        <button type="button" (click)="abrir(null)" [disabled]="!contexto()?.contratos?.length">Radicar nueva</button>
      }
    </div>
    @if (contexto() && !contexto()!.contratos.length) {
      <p class="aviso-inline">No tiene contratos vigentes para radicar en el portal. Puede usar el formulario público.</p>
    }

    @if (formulario(); as f) {
      <section class="tarjeta">
        <h2>{{ f.recurso ? 'Recurso contra la respuesta ' + f.recurso.radicado : 'Nueva PQR' }}</h2>
        <app-formulario-pqr
          modo="portal"
          [contratos]="contexto()?.contratos ?? []"
          [contacto]="contacto()"
          [recurso]="f.recurso"
          [enviando]="enviando()"
          [cancelable]="true"
          (cancelar)="formulario.set(null)"
          (radicar)="radicar($event)" />
      </section>
    }

    @for (p of pqrs(); track p.id) {
      <article class="tarjeta">
        <div class="encabezado">
          <h2>{{ p.numeroRadicado }} · {{ p.tipo }}</h2>
          <span class="estado" [attr.data-estado]="p.estado">{{ p.estado }}</span>
        </div>
        <dl class="ficha">
          <dt>Contrato</dt><dd>{{ p.numeroContrato ?? '—' }}@if (p.numeroFactura) { · factura {{ p.numeroFactura }} }</dd>
          <dt>Recibida</dt><dd>{{ p.fechaRegistro | date: 'medium' }}</dd>
          <dt>Fecha límite</dt><dd>{{ p.fechaLimiteRespuesta | date: 'longDate' }}</dd>
          @if (p.radicadoOrigen) { <dt>Recurre</dt><dd>{{ p.radicadoOrigen }}</dd> }
          @if (p.silencioPositivo) {
            <dt>Silencio positivo</dt><dd><span class="etiqueta peligro">EPQ no respondió a tiempo: opera a su favor</span></dd>
          }
          @if (p.respuesta) {
            <dt>Respuesta</dt><dd class="texto-largo">{{ p.respuesta }}</dd>
            <dt>Respondida</dt><dd>{{ p.fechaRespuesta | date: 'medium' }}</dd>
          }
          @if (p.decisionRecurso) { <dt>Decisión de reposición</dt><dd>{{ p.decisionRecurso }}</dd> }
          @if (p.estado === 'TRASLADADA_SSPD') {
            <dt>Apelación</dt><dd>En la Superintendencia de Servicios Públicos Domiciliarios</dd>
          }
          @if (p.decisionSspd) { <dt>Decisión SSPD</dt><dd>{{ p.decisionSspd }}</dd> }
          @if (p.adjuntos.length) {
            <dt>Adjuntos</dt>
            <dd>
              @for (a of p.adjuntos; track a.id) {
                <button type="button" class="enlace" (click)="descargar(p.id, a.id, a.nombreArchivo)">{{ a.nombreArchivo }}</button>
                <small>({{ a.tipo === 'EVIDENCIA' ? 'evidencia de EPQ' : 'soporte' }})</small>&nbsp;
              }
            </dd>
          }
        </dl>
        @if (p.radicadoRecurso) {
          <p class="ayuda">Esta respuesta fue recurrida con el radicado {{ p.radicadoRecurso }}.</p>
        } @else if (p.puedeRecurrir) {
          <p class="ayuda">Puede presentar recurso de reposición hasta el {{ p.fechaLimiteRecurso | date: 'longDate' }}.
            <button type="button" class="secundario" (click)="abrir(p)">Presentar recurso</button>
          </p>
        }
      </article>
    } @empty {
      <p class="vacio">No ha radicado PQR.</p>
    }
  `,
})
export class MisPqr {
  private readonly servicio = inject(PqrService);
  private readonly avisos = inject(AvisosService);
  private readonly componente = viewChild(FormularioPqr);

  readonly pqrs = signal<PqrPortal[]>([]);
  readonly contexto = signal<ContextoPortal | null>(null);
  readonly contacto = signal<{ nombre: string; correo: string; telefono: string } | null>(null);
  readonly formulario = signal<{ recurso: { numeroContrato: string; radicado: string } | null } | null>(null);
  readonly enviando = signal(false);

  constructor() {
    this.cargar();
    this.servicio.contextoPortal().subscribe((c) => {
      this.contexto.set(c);
      this.contacto.set({ nombre: c.nombre, correo: c.correo, telefono: c.telefono });
    });
  }

  cargar() {
    this.servicio.misPqr().subscribe((p) => this.pqrs.set(p));
  }

  abrir(p: PqrPortal | null) {
    this.formulario.set({ recurso: p && p.numeroContrato ? { numeroContrato: p.numeroContrato, radicado: p.numeroRadicado } : null });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  radicar(e: { datos: RadicarPqrDatos; adjuntos: File[] }) {
    this.enviando.set(true);
    this.servicio.radicarPortal(e.datos, e.adjuntos).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.formulario.set(null);
        this.avisos.exito(`Radicamos su solicitud ${r.numeroRadicado}. Le avisaremos por correo.`);
        this.cargar();
        this.servicio.contextoPortal().subscribe((c) => this.contexto.set(c));
      },
      error: (err: ApiError) => {
        this.enviando.set(false);
        this.componente()?.erroresServidor(err);
      },
    });
  }

  descargar(pqrId: number, adjuntoId: number, nombre: string) {
    this.servicio.descargarAdjuntoPortal(pqrId, adjuntoId).subscribe((b) => guardarBlob(b, nombre));
  }
}
