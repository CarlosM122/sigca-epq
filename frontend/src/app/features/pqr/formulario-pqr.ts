import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';

import { CatalogosService } from '../../core/catalogos.service';
import { ApiError, TIPOS_IDENTIFICACION, TIPOS_PQR } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import {
  correo,
  documentoValido,
  longitudRecortada,
  nombrePersona,
  sinHtml,
  telefonoColombia,
  validarArchivos,
} from '../../shared/validadores';
import { ContratoPqr, PqrService, RadicarPqrDatos, TipoPqr } from './pqr.service';

export type ModoRadicacion = 'publico' | 'portal' | 'presencial';

export const RADICADO = /^\d{4}(0[1-9]|1[0-2])-\d{4}$/;

/** Pone o quita "required" en un control según una condición, sin pisar sus otros errores. */
function exigir(c: AbstractControl, si: boolean) {
  const otros = { ...(c.errors ?? {}) };
  delete otros['required'];
  const vacio = c.value === null || c.value === undefined || c.value === '';
  const errs = si && vacio ? { ...otros, required: true } : otros;
  c.setErrors(Object.keys(errs).length ? errs : null, { emitEvent: false });
}

/**
 * Formulario de radicación de PQR para los tres canales (P-14, P-15):
 * - publico: sin sesión; siempre pide identificación (sin contrato solo PETICION, con municipio; con contrato la
 *   identificación debe ser la del titular) y la autorización de datos.
 * - portal: el ciudadano elige uno de sus contratos; las facturas reclamables y las respuestas recurribles salen del
 *   contexto del portal.
 * - presencial: el gestor busca el contrato por número para ver lo reclamable y recurrible; sin contrato, petición
 *   con identificación y municipio.
 */
@Component({
  selector: 'app-formulario-pqr',
  imports: [ReactiveFormsModule, CampoError, DatePipe, DecimalPipe],
  template: `
    <form [formGroup]="form" (ngSubmit)="enviar()" novalidate>
      <fieldset>
        <legend>Solicitud</legend>
        <label>Tipo de solicitud
          <select formControlName="tipo">
            @for (t of tiposDisponibles(); track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
          </select>
        </label>
        <p class="ayuda">{{ ayudaTipo() }}</p>

        @if (modo() === 'portal') {
          <label>Contrato
            <select formControlName="numeroContrato">
              <option value="" disabled>Seleccione el contrato</option>
              @for (c of contratos(); track c.id) {
                <option [value]="c.numeroContrato">{{ c.numeroContrato }} · {{ c.tipoServicio }} · {{ c.direccion }}</option>
              }
            </select>
          </label>
          <app-campo-error [control]="form.controls.numeroContrato" />
        } @else {
          <div class="fila">
            <label>Número de contrato {{ tipo() === 'PETICION' ? '(opcional)' : '' }}
              <input formControlName="numeroContrato" placeholder="CTR-00000001" maxlength="12" (blur)="buscarContrato()" />
              <app-campo-error [control]="form.controls.numeroContrato" />
            </label>
            @if (modo() === 'presencial') {
              <button type="button" class="secundario" (click)="buscarContrato()" [disabled]="!form.controls.numeroContrato.valid || !form.controls.numeroContrato.value">Buscar contrato</button>
            }
          </div>
          @if (contratoBuscado(); as c) {
            <p class="aviso-inline">{{ c.titular }} · {{ c.tipoServicio }} · {{ c.direccion }}, {{ c.municipio }} · {{ c.estado }}</p>
          }
        }

        @if (tipo() === 'RECLAMO') {
          @if (contrato()?.facturas?.length) {
            <label>Factura reclamada
              <select formControlName="numeroFactura">
                <option value="">Sin factura específica</option>
                @for (f of contrato()!.facturas; track f.numeroFactura) {
                  <option [value]="f.numeroFactura" [disabled]="f.enReclamo">
                    {{ f.numeroFactura }} · periodo {{ f.periodo }} · {{ f.totalLinea | number: '1.0-2' }}{{ f.enReclamo ? ' (ya en reclamo)' : '' }}
                  </option>
                }
              </select>
            </label>
          } @else {
            <label>Número de factura reclamada (opcional)
              <input formControlName="numeroFactura" placeholder="FAC-AAAAMM-0000000" maxlength="20" />
            </label>
          }
          <app-campo-error [control]="form.controls.numeroFactura" />
          <p class="ayuda">Mientras se resuelve el reclamo, el valor reclamado no se cobra ni genera suspensión.</p>
        }

        @if (tipo() === 'RECURSO') {
          @if (contrato()?.recurribles?.length) {
            <label>Respuesta que recurre
              <select formControlName="radicadoOriginal">
                <option value="" disabled>Seleccione</option>
                @for (r of contrato()!.recurribles; track r.numeroRadicado) {
                  <option [value]="r.numeroRadicado">{{ r.numeroRadicado }} · {{ r.tipo }} · plazo hasta {{ r.fechaLimiteRecurso | date: 'mediumDate' }}</option>
                }
              </select>
            </label>
          } @else {
            <label>Radicado de la PQR cuya respuesta recurre
              <input formControlName="radicadoOriginal" placeholder="AAAAMM-NNNN" maxlength="11" />
            </label>
          }
          <app-campo-error [control]="form.controls.radicadoOriginal" />
          <label class="check">
            <input type="checkbox" formControlName="solicitaApelacion" />
            Solicito también el recurso de apelación ante la Superintendencia de Servicios Públicos
          </label>
        }

        @if (!form.controls.numeroContrato.value && modo() !== 'portal') {
          <label>Municipio
            <select formControlName="municipioId">
              <option [ngValue]="null" disabled>Seleccione</option>
              @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
            </select>
          </label>
          <app-campo-error [control]="form.controls.municipioId" />
        }

        <label>Descripción ({{ form.controls.descripcion.value.trim().length }}/4000)
          <textarea formControlName="descripcion" rows="6" maxlength="4000"></textarea>
        </label>
        <app-campo-error [control]="form.controls.descripcion" />
        <label>Soportes (PDF, JPG o PNG; máximo 5 archivos de 10 MB)
          <input type="file" multiple accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" (change)="elegirArchivos($event)" />
        </label>
        @if (errorArchivos()) { <small class="error" role="alert">{{ errorArchivos() }}</small> }
      </fieldset>

      <fieldset>
        <legend>Datos de contacto</legend>
        @if (pideIdentificacion()) {
          <div class="fila">
            <label>Tipo de identificación
              <select formControlName="tipoIdentificacion">
                @for (t of tiposId; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
              </select>
            </label>
            <label>Número de identificación
              <input formControlName="numeroIdentificacion" maxlength="15" autocomplete="off" />
              <app-campo-error [control]="form.controls.numeroIdentificacion" />
            </label>
          </div>
          @if (modo() === 'publico' && form.controls.numeroContrato.value) {
            <p class="ayuda">Con contrato, la identificación debe ser la del titular.</p>
          }
        }
        <label>Nombre completo
          <input formControlName="nombreContacto" autocomplete="name" maxlength="150" />
        </label>
        <app-campo-error [control]="form.controls.nombreContacto" />
        <div class="fila">
          <label>Correo electrónico
            <input type="email" formControlName="correoContacto" autocomplete="email" maxlength="254" />
            <app-campo-error [control]="form.controls.correoContacto" />
          </label>
          <label>Teléfono
            <input formControlName="telefonoContacto" autocomplete="tel" placeholder="3001234567" maxlength="13" />
            <app-campo-error [control]="form.controls.telefonoContacto" />
          </label>
        </div>
        @if (modo() === 'publico') {
          <label class="check">
            <input type="checkbox" formControlName="autorizaTratamientoDatos" />
            Autorizo el tratamiento de mis datos personales (Ley 1581 de 2012)
          </label>
          <app-campo-error [control]="form.controls.autorizaTratamientoDatos" />
        }
      </fieldset>

      <div class="fila">
        <button type="submit" [disabled]="enviando()">{{ enviando() ? 'Radicando…' : 'Radicar' }}</button>
        @if (cancelable()) { <button type="button" class="secundario" (click)="cancelar.emit()">Cancelar</button> }
      </div>
    </form>
  `,
})
export class FormularioPqr {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(PqrService);

  readonly modo = input<ModoRadicacion>('publico');
  readonly enviando = input(false);
  readonly cancelable = input(false);
  /** Portal: contratos del titular con sus facturas reclamables y respuestas recurribles. */
  readonly contratos = input<ContratoPqr[]>([]);
  /** Portal: contacto del titular para precargar. */
  readonly contacto = input<{ nombre: string; correo: string; telefono: string } | null>(null);
  /** Para abrir el formulario como recurso de una respuesta concreta. */
  readonly recurso = input<{ numeroContrato: string; radicado: string } | null>(null);

  readonly radicar = output<{ datos: RadicarPqrDatos; adjuntos: File[] }>();
  readonly cancelar = output<void>();

  readonly tiposId = TIPOS_IDENTIFICACION;
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly errorArchivos = signal<string | null>(null);
  readonly contratoBuscado = signal<ContratoPqr | null>(null);
  private archivos: File[] = [];

  readonly form = this.fb.group(
    {
      tipo: this.fb.control<TipoPqr>('PETICION', Validators.required),
      numeroContrato: ['', Validators.pattern(/^CTR-\d{8}$/)],
      numeroFactura: ['', Validators.pattern(/^FAC-\d{6}-\d{7}$/)],
      radicadoOriginal: ['', Validators.pattern(RADICADO)],
      solicitaApelacion: [false],
      municipioId: this.fb.control<number | null>(null),
      descripcion: ['', [Validators.required, longitudRecortada(20, 4000), sinHtml]],
      tipoIdentificacion: ['CC'],
      numeroIdentificacion: [''],
      nombreContacto: ['', [Validators.required, nombrePersona]],
      correoContacto: ['', [Validators.required, correo]],
      telefonoContacto: ['', [Validators.required, telefonoColombia]],
      autorizaTratamientoDatos: [false],
    },
    { validators: [this.reglas(), documentoValido()] },
  );

  readonly tipo = toSignal(this.form.controls.tipo.valueChanges, { initialValue: this.form.controls.tipo.value });
  private readonly numeroContrato = toSignal(this.form.controls.numeroContrato.valueChanges, { initialValue: '' });

  /** Contrato elegido (portal) o buscado (ventanilla), con lo reclamable y recurrible. */
  readonly contrato = computed<ContratoPqr | null>(() => {
    const n = this.numeroContrato();
    if (this.modo() === 'portal') {
      return this.contratos().find((c) => c.numeroContrato === n) ?? null;
    }
    const b = this.contratoBuscado();
    return b && b.numeroContrato === n ? b : null;
  });

  readonly tiposDisponibles = computed(() => TIPOS_PQR);

  readonly pideIdentificacion = computed(() => {
    const conContrato = !!this.numeroContrato();
    return this.modo() === 'publico' || (this.modo() === 'presencial' && !conContrato);
  });

  readonly ayudaTipo = computed(() => {
    switch (this.tipo()) {
      case 'PETICION':
        return 'Solicitud de información o de un servicio. Puede radicarse sin contrato.';
      case 'QUEJA':
        return 'Inconformidad con la atención o el servicio. Requiere el contrato.';
      case 'RECLAMO':
        return 'Desacuerdo con un cobro de la factura. Requiere el contrato.';
      default:
        return 'Recurso de reposición contra una respuesta de EPQ, dentro de los 5 días hábiles siguientes a la notificación.';
    }
  });

  constructor() {
    effect(() => {
      const c = this.contacto();
      if (c) {
        this.form.patchValue({ nombreContacto: c.nombre, correoContacto: c.correo, telefonoContacto: c.telefono });
      }
    });
    effect(() => {
      const contratos = this.contratos();
      if (this.modo() === 'portal' && contratos.length === 1 && !this.form.controls.numeroContrato.value) {
        this.form.controls.numeroContrato.setValue(contratos[0].numeroContrato);
      }
    });
    effect(() => {
      const r = this.recurso();
      if (r) {
        this.form.patchValue({ tipo: 'RECURSO', numeroContrato: r.numeroContrato, radicadoOriginal: r.radicado });
      }
    });
    // Al cambiar de tipo se limpian los campos que solo aplican a otro tipo.
    this.form.controls.tipo.valueChanges.subscribe((t) => {
      if (t !== 'RECLAMO') this.form.controls.numeroFactura.setValue('');
      if (t !== 'RECURSO') this.form.patchValue({ radicadoOriginal: '', solicitaApelacion: false });
    });
  }

  /** Reglas condicionales (las mismas de RadicacionValida en el backend). */
  private reglas(): ValidatorFn {
    return (g) => {
      const v = (n: string) => g.get(n)!;
      const tipo = v('tipo').value as TipoPqr;
      const conContrato = !!v('numeroContrato').value;
      const modo = this.modo?.() ?? 'publico';
      exigir(v('numeroContrato'), tipo !== 'PETICION' || modo === 'portal');
      exigir(v('municipioId'), !conContrato && modo !== 'portal');
      exigir(v('radicadoOriginal'), tipo === 'RECURSO');
      const pideId = modo === 'publico' || (modo === 'presencial' && !conContrato);
      exigir(v('tipoIdentificacion'), pideId);
      exigir(v('numeroIdentificacion'), pideId);
      const autoriza = v('autorizaTratamientoDatos');
      const errs = { ...(autoriza.errors ?? {}) };
      delete errs['requiredTrue'];
      if (modo === 'publico' && !autoriza.value) errs['requiredTrue'] = true;
      autoriza.setErrors(Object.keys(errs).length ? errs : null, { emitEvent: false });
      return null;
    };
  }

  buscarContrato() {
    const c = this.form.controls.numeroContrato;
    if (this.modo() !== 'presencial' || !c.value || c.invalid) {
      return;
    }
    if (this.contratoBuscado()?.numeroContrato === c.value) return;
    this.servicio.contrato(c.value).subscribe({
      next: (k) => this.contratoBuscado.set(k),
      error: () => this.contratoBuscado.set(null),
    });
  }

  elegirArchivos(evento: Event) {
    this.archivos = Array.from((evento.target as HTMLInputElement).files ?? []);
    this.errorArchivos.set(validarArchivos(this.archivos));
  }

  enviar() {
    this.form.updateValueAndValidity();
    if (!listoParaEnviar(this.form) || this.errorArchivos()) return;
    const v = this.form.getRawValue();
    const conContrato = !!v.numeroContrato;
    const pideId = this.pideIdentificacion();
    this.radicar.emit({
      datos: {
        tipo: v.tipo,
        numeroContrato: v.numeroContrato || null,
        numeroFactura: v.tipo === 'RECLAMO' && v.numeroFactura ? v.numeroFactura : null,
        radicadoOriginal: v.tipo === 'RECURSO' ? v.radicadoOriginal : null,
        solicitaApelacion: v.tipo === 'RECURSO' ? v.solicitaApelacion : null,
        municipioId: conContrato ? null : v.municipioId,
        tipoIdentificacion: pideId ? v.tipoIdentificacion : null,
        numeroIdentificacion: pideId ? v.numeroIdentificacion.trim().toUpperCase() : null,
        nombreContacto: v.nombreContacto.trim(),
        correoContacto: v.correoContacto.trim().toLowerCase(),
        telefonoContacto: v.telefonoContacto.trim(),
        descripcion: v.descripcion.trim(),
        autorizaTratamientoDatos: this.modo() === 'publico' ? v.autorizaTratamientoDatos : null,
      },
      adjuntos: this.archivos,
    });
  }

  erroresServidor(e: ApiError) {
    aplicarErroresServidor(this.form, e);
  }
}
