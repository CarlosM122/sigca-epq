import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { longitudRecortada, sinHtml, validarArchivos } from '../../shared/validadores';
import { DECISIONES, DecisionRecurso, Gestor, PqrDetalle, PqrService, guardarBlob } from './pqr.service';

/**
 * Detalle y trámite de una PQR (P-03, P-15, RF-11..13): tomar, actuaciones, evidencias, ajuste de la línea
 * reclamada, respuesta (con decisión en un recurso), traslado y decisión de la SSPD, reasignación y
 * reconocimiento del silencio positivo. Cada acción se muestra solo si el estado y los permisos la admiten;
 * la BD vuelve a validar todo.
 */
@Component({
  selector: 'app-detalle-pqr',
  imports: [ReactiveFormsModule, CampoError, DatePipe, DecimalPipe, RouterLink],
  template: `
    <p><a routerLink="/pqr">← Volver a la bandeja</a></p>
    @if (pqr(); as p) {
      <div class="encabezado">
        <h1>PQR {{ p.numeroRadicado }}</h1>
        <span class="estado" [attr.data-estado]="p.estado">{{ p.estado }}</span>
      </div>
      @if (p.silencioPositivo && !p.fechaReconocimientoSilencio) {
        <p class="aviso-inline peligro">Venció sin respuesta: opera el silencio administrativo positivo. Reconozca sus efectos antes de
          {{ p.fechaLimiteReconocimiento | date: 'medium' }}.</p>
      }
      @if (p.diasHabilesRestantes !== null && p.diasHabilesRestantes !== undefined && abierta() && p.diasHabilesRestantes <= 2) {
        <p class="aviso-inline">Quedan {{ p.diasHabilesRestantes }} días hábiles para responder.</p>
      }
      <dl class="ficha">
        <dt>Tipo</dt><dd>{{ p.tipo }} · canal {{ p.canal }}@if (p.registradaPor) { · registrada por {{ p.registradaPor }} }</dd>
        <dt>Contrato</dt><dd>{{ p.numeroContrato ?? 'Sin contrato' }}@if (p.titular) { · {{ p.titular }} }</dd>
        @if (p.solicitanteIdentificacion) { <dt>Identificación</dt><dd>{{ p.solicitanteIdentificacion }}</dd> }
        <dt>Municipio</dt><dd>{{ p.municipio }}</dd>
        <dt>Solicitante</dt><dd>{{ p.nombreContacto }} · {{ p.correoContacto }} · {{ p.telefonoContacto }}</dd>
        <dt>Recibida</dt><dd>{{ p.fechaRegistro | date: 'medium' }}</dd>
        <dt>Fecha límite</dt><dd>{{ p.fechaLimiteRespuesta | date: 'longDate' }}</dd>
        <dt>Responsable</dt><dd>{{ p.gestor }}</dd>
        @if (p.pqrOrigen) { <dt>Recurre</dt><dd><a [routerLink]="['/pqr', p.pqrOrigen.id]">{{ p.pqrOrigen.numeroRadicado }}</a>
          @if (p.solicitaApelacion) { <span class="etiqueta">Con apelación</span> }</dd> }
        @if (p.recurso) { <dt>Recurso</dt><dd><a [routerLink]="['/pqr', p.recurso.id]">{{ p.recurso.numeroRadicado }}</a> ({{ p.recurso.estado }})</dd> }
        @if (p.linea; as l) {
          <dt>Línea reclamada</dt>
          <dd>{{ l.numeroFactura }} · {{ l.tipoServicio }} · periodo {{ l.periodo }} · {{ l.totalLinea | number: '1.0-2' }}
            @if (l.valorAjuste) { · ajuste {{ l.valorAjuste | number: '1.0-2' }} } · {{ l.estado }}
            @if (l.enReclamo) { <span class="etiqueta">En reclamo</span> }</dd>
        }
        <dt>Descripción</dt><dd class="texto-largo">{{ p.descripcion }}</dd>
        @if (p.respuesta) {
          <dt>Respuesta</dt>
          <dd class="texto-largo">{{ p.respuesta }}
            @if (p.respuestaExtemporanea) { <span class="etiqueta peligro">Extemporánea</span> }</dd>
          <dt>Respondida</dt><dd>{{ p.fechaRespuesta | date: 'medium' }} · {{ p.respondidaPor }}</dd>
        }
        @if (p.decisionRecurso) { <dt>Decisión de reposición</dt><dd>{{ p.decisionRecurso }}</dd> }
        @if (p.fechaTrasladoSspd) { <dt>Traslado SSPD</dt><dd>{{ p.fechaTrasladoSspd | date: 'medium' }}</dd> }
        @if (p.decisionSspd) { <dt>Decisión SSPD</dt><dd>{{ p.decisionSspd }} · {{ p.fechaDecisionSspd | date: 'medium' }}</dd> }
        @if (p.fechaReconocimientoSilencio) { <dt>Silencio reconocido</dt><dd>{{ p.fechaReconocimientoSilencio | date: 'medium' }}</dd> }
      </dl>

      <h2>Adjuntos</h2>
      <ul>
        @for (a of p.adjuntos; track a.id) {
          <li><button type="button" class="enlace" (click)="descargar(a.id, a.nombreArchivo)">{{ a.nombreArchivo }}</button>
            ({{ a.tipo === 'EVIDENCIA' ? 'evidencia' : 'soporte' }}, {{ a.tamanoBytes / 1024 | number: '1.0-0' }} KB)</li>
        } @empty { <li>Sin adjuntos</li> }
      </ul>

      @if (puedeTramitar()) {
        <section class="acciones-pqr">
          @if (p.estado === 'RECIBIDA') {
            <button type="button" (click)="ejecutar(servicio.tomar(p.id), 'PQR en trámite')">Iniciar trámite</button>
          }

          @if (enTramite()) {
            <form [formGroup]="actuacion" (ngSubmit)="registrarActuacion()" novalidate>
              <h2>Registrar actuación</h2>
              <textarea formControlName="descripcion" rows="3" maxlength="2000" aria-label="Actuación"></textarea>
              <app-campo-error [control]="actuacion.controls.descripcion" />
              <button type="submit" class="secundario">Guardar actuación</button>
            </form>

            <h2>Evidencia de la respuesta</h2>
            <input type="file" multiple accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" (change)="subirEvidencia($event)" aria-label="Evidencias" />
            @if (errorArchivos()) { <small class="error" role="alert">{{ errorArchivos() }}</small> }
          }

          @if (p.linea?.enReclamo && (enTramite() || p.estado === 'TRASLADADA_SSPD')) {
            <form [formGroup]="ajuste" (ngSubmit)="ajustar()" novalidate>
              <h2>Ajuste de la línea reclamada</h2>
              <p class="ayuda">Valor que se descuenta (negativo). Si descuenta todo el valor, la línea queda exonerada.</p>
              <input type="number" formControlName="valorAjuste" step="0.01" [max]="0" [min]="-(p.linea?.totalLinea ?? 0)" aria-label="Valor del ajuste" />
              <app-campo-error [control]="ajuste.controls.valorAjuste" />
              <button type="submit" class="secundario">Aplicar ajuste</button>
            </form>
          }

          @if (enTramite()) {
            <form [formGroup]="respuesta" (ngSubmit)="responder()" novalidate>
              <h2>{{ p.tipo === 'RECURSO' ? 'Resolver el recurso' : 'Responder' }}</h2>
              @if (p.tipo === 'RECURSO') {
                <label>Decisión
                  <select formControlName="decisionRecurso">
                    <option [ngValue]="null" disabled>Seleccione</option>
                    @for (d of decisiones; track d.valor) { <option [ngValue]="d.valor">{{ d.etiqueta }}</option> }
                  </select>
                </label>
                <app-campo-error [control]="respuesta.controls.decisionRecurso" />
                @if (trasladar()) {
                  <p class="aviso-inline">El usuario pidió apelación y la decisión se confirma: la respuesta se envía y el expediente pasa a la Superservicios.</p>
                }
              }
              <textarea formControlName="respuesta" rows="6" maxlength="4000" aria-label="Respuesta"></textarea>
              <app-campo-error [control]="respuesta.controls.respuesta" />
              @if (!tieneEvidencia()) { <small class="error">Adjunte al menos una evidencia antes de responder (RN-06).</small> }
              <button type="submit" [disabled]="!tieneEvidencia()">{{ trasladar() ? 'Responder y trasladar a la SSPD' : 'Enviar respuesta' }}</button>
            </form>
          }

          @if (p.estado === 'TRASLADADA_SSPD') {
            <form [formGroup]="sspd" (ngSubmit)="registrarSspd()" novalidate>
              <h2>Decisión de la Superintendencia</h2>
              <select formControlName="decisionSspd" aria-label="Decisión SSPD">
                <option [ngValue]="null" disabled>Seleccione</option>
                @for (d of decisiones; track d.valor) { <option [ngValue]="d.valor">{{ d.etiqueta }}</option> }
              </select>
              <app-campo-error [control]="sspd.controls.decisionSspd" />
              <textarea formControlName="observacion" rows="3" maxlength="2000" placeholder="Resolución y observaciones (opcional)"></textarea>
              <app-campo-error [control]="sspd.controls.observacion" />
              <button type="submit" class="secundario">Registrar decisión</button>
            </form>
          }
        </section>
      }

      @if (auth.tienePermiso('PQR_REASIGNAR') && (p.estado === 'RECIBIDA' || p.estado === 'EN_TRAMITE')) {
        <form [formGroup]="reasignacion" (ngSubmit)="reasignar()" novalidate>
          <h2>Reasignar</h2>
          <select formControlName="gestorId" aria-label="Nuevo responsable">
            <option [ngValue]="null" disabled>Seleccione el responsable</option>
            @for (g of gestores(); track g.id) {
              @if (g.id !== p.gestorId) {
                <option [ngValue]="g.id">{{ g.nombre }} ({{ g.rol === 'JEFE_PQR' ? 'Jefe de PQR' : g.municipio }}, {{ g.pqrAbiertas }} abiertas)</option>
              }
            }
          </select>
          <app-campo-error [control]="reasignacion.controls.gestorId" />
          <input formControlName="motivo" placeholder="Motivo" maxlength="300" aria-label="Motivo" />
          <app-campo-error [control]="reasignacion.controls.motivo" />
          <button type="submit" class="secundario">Reasignar</button>
        </form>
      }

      @if (auth.tienePermiso('PQR_RECONOCER_SILENCIO') && p.silencioPositivo && !p.fechaReconocimientoSilencio) {
        <form [formGroup]="reconocimiento" (ngSubmit)="reconocer()" novalidate>
          <h2>Reconocer el silencio administrativo positivo</h2>
          <textarea formControlName="observacion" rows="3" maxlength="2000" placeholder="Efectos reconocidos al usuario"></textarea>
          <app-campo-error [control]="reconocimiento.controls.observacion" />
          <button type="submit">Registrar reconocimiento</button>
        </form>
      }

      <h2>Historial</h2>
      <ul class="linea-tiempo">
        @for (h of p.historial; track $index) {
          <li>{{ h.fecha | date: 'medium' }} · {{ h.estadoAnterior ?? '—' }} → {{ h.estadoNuevo ?? h.estadoAnterior }}
            @if (h.gestorNuevo && h.gestorNuevo !== h.gestorAnterior) { · asignada a {{ h.gestorNuevo }} }
            @if (h.realizadoPor) { · {{ h.realizadoPor }} }</li>
        }
      </ul>
      <h2>Actuaciones</h2>
      <ul class="linea-tiempo">
        @for (a of p.actuaciones; track $index) {
          <li>{{ a.fecha | date: 'medium' }} · {{ a.funcionario }}: <span class="texto-largo">{{ a.descripcion }}</span></li>
        } @empty { <li>Sin actuaciones</li> }
      </ul>
    }
  `,
})
export class DetallePqr implements OnInit {
  readonly servicio = inject(PqrService);
  readonly auth = inject(AuthService);
  private readonly avisos = inject(AvisosService);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly id = input.required<string>();
  readonly pqr = signal<PqrDetalle | null>(null);
  readonly gestores = signal<Gestor[]>([]);
  readonly errorArchivos = signal<string | null>(null);
  readonly decisiones = DECISIONES;

  readonly abierta = computed(() => ['RECIBIDA', 'EN_TRAMITE', 'VENCIDA'].includes(this.pqr()?.estado ?? ''));
  readonly enTramite = computed(() => ['EN_TRAMITE', 'VENCIDA'].includes(this.pqr()?.estado ?? ''));
  readonly puedeTramitar = computed(() => this.auth.tienePermiso('PQR_TRAMITAR') && this.pqr()?.estado !== 'RESPONDIDA');
  readonly tieneEvidencia = computed(() => !!this.pqr()?.adjuntos.some((a) => a.tipo === 'EVIDENCIA'));
  private readonly decision = signal<DecisionRecurso | null>(null);
  readonly trasladar = computed(
    () => this.pqr()?.tipo === 'RECURSO' && !!this.pqr()?.solicitaApelacion && this.decision() === 'CONFIRMA',
  );

  readonly actuacion = this.fb.group({ descripcion: ['', [Validators.required, longitudRecortada(5, 2000), sinHtml]] });
  readonly respuesta = this.fb.group({
    respuesta: ['', [Validators.required, longitudRecortada(20, 4000), sinHtml]],
    decisionRecurso: this.fb.control<DecisionRecurso | null>(null),
  });
  readonly ajuste = this.fb.group({
    valorAjuste: this.fb.control<number | null>(null, [Validators.required, Validators.max(0)]),
  });
  readonly sspd = this.fb.group({
    decisionSspd: this.fb.control<DecisionRecurso | null>(null, Validators.required),
    observacion: ['', [longitudRecortada(5, 2000), sinHtml]],
  });
  readonly reasignacion = this.fb.group({
    gestorId: this.fb.control<number | null>(null, Validators.required),
    motivo: ['', [Validators.required, longitudRecortada(5, 300), sinHtml]],
  });
  readonly reconocimiento = this.fb.group({ observacion: ['', [longitudRecortada(5, 2000), sinHtml]] });

  constructor() {
    this.respuesta.controls.decisionRecurso.valueChanges.subscribe((d) => this.decision.set(d));
  }

  ngOnInit() {
    this.servicio.detalle(Number(this.id())).subscribe((p) => {
      this.actualizar(p);
      if (this.auth.tienePermiso('PQR_REASIGNAR')) {
        this.servicio.gestores(p.municipioId).subscribe((g) => this.gestores.set(g));
      }
    });
  }

  private actualizar(p: PqrDetalle) {
    this.pqr.set(p);
    const dc = this.respuesta.controls.decisionRecurso;
    dc.setValidators(p.tipo === 'RECURSO' ? Validators.required : null);
    dc.updateValueAndValidity({ emitEvent: false });
  }

  ejecutar(op: Observable<PqrDetalle>, mensaje: string, form?: FormGroup) {
    op.subscribe({
      next: (p) => {
        this.actualizar(p);
        this.avisos.exito(mensaje);
        form?.reset();
      },
      error: (e: ApiError) => {
        if (form) aplicarErroresServidor(form, e);
      },
    });
  }

  registrarActuacion() {
    if (!listoParaEnviar(this.actuacion)) return;
    this.ejecutar(this.servicio.registrarActuacion(this.pqr()!.id, this.actuacion.getRawValue().descripcion.trim()), 'Actuación registrada', this.actuacion);
  }

  subirEvidencia(evento: Event) {
    const campo = evento.target as HTMLInputElement;
    const archivos = Array.from(campo.files ?? []);
    const error = validarArchivos(archivos);
    this.errorArchivos.set(error);
    if (!error && archivos.length) {
      this.ejecutar(this.servicio.adjuntarEvidencia(this.pqr()!.id, archivos), 'Evidencia adjuntada');
      campo.value = '';
    }
  }

  ajustar() {
    if (!listoParaEnviar(this.ajuste)) return;
    const valor = Number(this.ajuste.getRawValue().valorAjuste);
    const total = this.pqr()!.linea?.totalLinea ?? 0;
    if (-valor > total) {
      this.ajuste.controls.valorAjuste.setErrors({ servidor: 'El ajuste no puede superar el valor de la línea' });
      return;
    }
    this.ejecutar(this.servicio.ajustarLinea(this.pqr()!.id, valor), 'Ajuste aplicado a la línea', this.ajuste);
  }

  responder() {
    if (!listoParaEnviar(this.respuesta)) return;
    const v = this.respuesta.getRawValue();
    const p = this.pqr()!;
    if (this.trasladar()) {
      this.ejecutar(this.servicio.trasladarSspd(p.id, v.respuesta.trim()), 'Respuesta enviada y recurso trasladado a la SSPD', this.respuesta);
    } else {
      this.ejecutar(this.servicio.responder(p.id, v.respuesta.trim(), p.tipo === 'RECURSO' ? v.decisionRecurso : null), 'Respuesta enviada al ciudadano', this.respuesta);
    }
  }

  registrarSspd() {
    if (!listoParaEnviar(this.sspd)) return;
    const v = this.sspd.getRawValue();
    this.ejecutar(this.servicio.decisionSspd(this.pqr()!.id, v.decisionSspd!, v.observacion.trim() || null), 'Decisión de la SSPD registrada', this.sspd);
  }

  reasignar() {
    if (!listoParaEnviar(this.reasignacion)) return;
    const v = this.reasignacion.getRawValue();
    this.ejecutar(this.servicio.reasignar(this.pqr()!.id, v.gestorId!, v.motivo.trim()), 'PQR reasignada', this.reasignacion);
  }

  reconocer() {
    if (!listoParaEnviar(this.reconocimiento)) return;
    this.ejecutar(
      this.servicio.reconocerSilencio(this.pqr()!.id, this.reconocimiento.getRawValue().observacion.trim() || null),
      'Silencio positivo reconocido',
      this.reconocimiento,
    );
  }

  descargar(adjuntoId: number, nombre: string) {
    this.servicio.descargarAdjunto(this.pqr()!.id, adjuntoId).subscribe((blob) => guardarBlob(blob, nombre));
  }
}
