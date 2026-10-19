import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError, Pagina } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, hoyIso, listoParaEnviar } from '../../shared/formularios';
import { Paginador } from '../../shared/paginador';
import { fechaNoFutura, periodo, sinHtml } from '../../shared/validadores';
import {
  CARGA_MAX_BYTES, CAUSAS_NO_LECTURA, LecturaVista, LecturasService, ResultadoCarga, periodoAnterior,
} from './lecturas.service';

type Pestana = 'registro' | 'archivo' | 'revision' | 'historial';

/** La fecha se toma en el mes del periodo o en el siguiente (misma regla que TRG_LECTURA_CIUD). */
function fechaDelPeriodo(grupo: AbstractControl): ValidationErrors | null {
  const p = String(grupo.get('periodo')?.value ?? '');
  const fecha = grupo.get('fechaLectura')?.value as string;
  if (!/^\d{6}$/.test(p) || !fecha) return null;
  const inicio = `${p.slice(0, 4)}-${p.slice(4)}-01`;
  const fin = new Date(Number(p.slice(0, 4)), Number(p.slice(4)) + 1, 0);
  const finIso = `${fin.getFullYear()}-${String(fin.getMonth() + 1).padStart(2, '0')}-${String(fin.getDate()).padStart(2, '0')}`;
  return fecha < inicio || fecha > finIso ? { fechaPeriodo: true } : null;
}

/**
 * Lecturas (P-07, RN-35): registro manual, cargue CSV con resultado por fila, bandeja de crítica y
 * historial por medidor. La base calcula el estado; una lectura desviada queda EN_REVISION y solo un
 * asesor la confirma o la estima por promedio.
 */
@Component({
  selector: 'app-lecturas',
  imports: [ReactiveFormsModule, CampoError, Paginador, DecimalPipe, DatePipe],
  template: `
    <h1>Lecturas</h1>
    <div class="pestanas" role="tablist">
      @for (p of pestanas(); track p.id) {
        <button type="button" role="tab" [class.activa]="pestana() === p.id" [attr.aria-selected]="pestana() === p.id"
          (click)="cambiar(p.id)">{{ p.texto }}</button>
      }
    </div>

    @switch (pestana()) {
      @case ('registro') {
        <form class="tarjeta" [formGroup]="form" (ngSubmit)="registrar()" novalidate>
          <div class="fila">
            <label>Serial del medidor <input formControlName="serial" maxlength="30" /></label>
            <label>Periodo <input formControlName="periodo" inputmode="numeric" maxlength="6" placeholder="AAAAMM" /></label>
            <label>Fecha de lectura <input type="date" formControlName="fechaLectura" [max]="hoy" /></label>
          </div>
          <app-campo-error [control]="form.controls.serial" />
          <app-campo-error [control]="form.controls.periodo" />
          <app-campo-error [control]="form.controls.fechaLectura" />
          @if (form.hasError('fechaPeriodo')) {
            <small class="error" role="alert">La fecha debe estar en el mes del periodo o en el siguiente</small>
          }
          <label class="check"><input type="checkbox" formControlName="sinLectura" /> No se pudo tomar la lectura</label>
          <div class="fila">
            @if (form.controls.sinLectura.value) {
              <label>Causa
                <select formControlName="causaNoLectura">
                  <option value="" disabled>Seleccione</option>
                  @for (c of causas; track c.valor) { <option [value]="c.valor">{{ c.etiqueta }}</option> }
                </select>
                <app-campo-error [control]="form.controls.causaNoLectura" />
              </label>
            } @else {
              <label>Lectura actual (m³)
                <input type="number" formControlName="lecturaActual" min="0" step="0.001" />
                <app-campo-error [control]="form.controls.lecturaActual" />
              </label>
            }
            <label>Observación <input formControlName="observacion" maxlength="300" />
              <app-campo-error [control]="form.controls.observacion" /></label>
          </div>
          <button type="submit" [disabled]="enviando()">Registrar</button>
          @if (ultima(); as l) {
            <p class="aviso-inline">
              {{ l.serial }} · {{ l.periodo }}: <span class="estado" [attr.data-estado]="l.estado">{{ l.estado }}</span>
              consumo {{ l.consumo | number: '1.0-3' }} m³
              @if (l.promedioReferencia !== null && l.promedioReferencia !== undefined) {
                (promedio {{ l.promedioReferencia | number: '1.0-3' }}, desviación {{ l.desviacionPct ?? '—' }} %)
              }
            </p>
          }
        </form>
      }
      @case ('archivo') {
        <div class="tarjeta">
          <p class="ayuda">
            CSV en UTF-8 con columnas <code>serial,periodo,fecha,lectura,causa</code> (encabezado opcional; también acepta ';').
            Deje vacía la lectura cuando informe una causa. Máximo 1 MB y 2000 filas. Cada fila se procesa por separado.
          </p>
          <input type="file" accept=".csv,text/csv" (change)="elegir($event)" />
          <button type="button" [disabled]="!archivo() || enviando()" (click)="cargar()">Cargar</button>
          @if (carga(); as r) {
            <p class="total">
              {{ r.registradas }} de {{ r.procesadas }} filas registradas · {{ r.confirmadas }} confirmadas ·
              {{ r.enRevision }} en revisión · {{ r.estimadas }} estimadas · {{ r.conError }} con error
            </p>
            <table class="tabla compacta">
              <thead><tr><th>Línea</th><th>Serial</th><th>Periodo</th><th>Resultado</th><th class="num">Consumo</th><th>Mensaje</th></tr></thead>
              <tbody>
                @for (f of r.filas; track f.linea) {
                  <tr [class.alerta]="!f.exito">
                    <td>{{ f.linea }}</td><td>{{ f.serial ?? '—' }}</td><td>{{ f.periodo ?? '—' }}</td>
                    <td>@if (f.exito) { <span class="estado" [attr.data-estado]="f.estado">{{ f.estado }}</span> } @else { Error }</td>
                    <td class="num">{{ f.consumo | number: '1.0-3' }}</td><td>{{ f.mensaje ?? '' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }
      @case ('revision') {
        <p class="ayuda">Lecturas con desviación mayor al umbral frente al promedio de los últimos periodos.</p>
        <table class="tabla">
          <thead>
            <tr><th>Medidor</th><th>Contrato y titular</th><th>Periodo</th><th class="num">Anterior</th><th class="num">Actual</th>
              <th class="num">Consumo</th><th class="num">Promedio</th><th class="num">Desviación</th><th></th></tr>
          </thead>
          <tbody>
            @for (l of revision()?.content ?? []; track l.id) {
              <tr>
                <td>{{ l.serial }} · {{ l.tipoServicio }}</td>
                <td>{{ l.numeroContrato ?? 'Sin contrato' }} · {{ l.titular ?? '' }}<br /><small>{{ l.direccion }}, {{ l.municipio }}</small></td>
                <td>{{ l.periodo }}</td>
                <td class="num">{{ l.lecturaAnterior | number: '1.0-3' }}</td><td class="num">{{ l.lecturaActual | number: '1.0-3' }}</td>
                <td class="num">{{ l.consumo | number: '1.0-3' }}</td><td class="num">{{ l.promedioReferencia | number: '1.0-3' }}</td>
                <td class="num">{{ l.desviacionPct === null || l.desviacionPct === undefined ? 'Nuevo' : (l.desviacionPct | number: '1.0-2') + ' %' }}</td>
                <td><button type="button" class="enlace" (click)="seleccionar(l)">Revisar</button></td>
              </tr>
            } @empty { <tr><td colspan="9">No hay lecturas en revisión.</td></tr> }
          </tbody>
        </table>
        <app-paginador [pagina]="revision()" (cambiar)="cargarRevision($event)" />
        @if (enRevision(); as l) {
          <form class="tarjeta" [formGroup]="decision" (ngSubmit)="revisar(l)" novalidate>
            <h3>Lectura {{ l.serial }} del periodo {{ l.periodo }}</h3>
            <label><input type="radio" formControlName="decision" value="CONFIRMAR" /> Confirmar: se factura el consumo medido ({{ l.consumo | number: '1.0-3' }} m³)</label>
            <label><input type="radio" formControlName="decision" value="ESTIMAR" /> Estimar: se factura el promedio ({{ l.promedioReferencia | number: '1.0-3' }} m³)</label>
            <label>Observación {{ decision.controls.decision.value === 'ESTIMAR' ? '(obligatoria)' : '(opcional)' }}
              <input formControlName="observacion" maxlength="300" />
            </label>
            <app-campo-error [control]="decision.controls.observacion" />
            <div class="acciones">
              <button type="submit">Guardar revisión</button>
              <button type="button" class="secundario" (click)="enRevision.set(null)">Cerrar</button>
            </div>
          </form>
        }
      }
      @case ('historial') {
        <form class="filtros" [formGroup]="filtroHistorial" (ngSubmit)="cargarHistorial(0)">
          <input formControlName="serial" placeholder="Serial del medidor" maxlength="30" />
          <button type="submit">Consultar</button>
        </form>
        <table class="tabla">
          <thead>
            <tr><th>Periodo</th><th>Fecha</th><th class="num">Anterior</th><th class="num">Actual</th><th class="num">Consumo</th>
              <th>Estado</th><th>Origen</th><th>Causa u observación</th></tr>
          </thead>
          <tbody>
            @for (l of historial()?.content ?? []; track l.id) {
              <tr>
                <td>{{ l.periodo }}</td><td>{{ l.fechaLectura | date: 'mediumDate' }}</td>
                <td class="num">{{ l.lecturaAnterior | number: '1.0-3' }}</td>
                <td class="num">{{ l.lecturaActual === null || l.lecturaActual === undefined ? '—' : (l.lecturaActual | number: '1.0-3') }}</td>
                <td class="num">{{ l.consumo | number: '1.0-3' }}</td>
                <td><span class="estado" [attr.data-estado]="l.estado">{{ l.estado }}</span></td>
                <td>{{ l.origen }}</td><td>{{ l.causaNoLectura ?? '' }} {{ l.observacion ?? '' }}</td>
              </tr>
            } @empty { <tr><td colspan="8">Sin lecturas.</td></tr> }
          </tbody>
        </table>
        <app-paginador [pagina]="historial()" (cambiar)="cargarHistorial($event)" />
      }
    }
  `,
})
export class Lecturas {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(LecturasService);
  private readonly avisos = inject(AvisosService);
  readonly auth = inject(AuthService);
  readonly hoy = hoyIso();
  readonly causas = CAUSAS_NO_LECTURA;

  readonly pestanas = computed(() =>
    [
      { id: 'registro' as Pestana, texto: 'Registro manual', permiso: 'LECTURA_REGISTRAR' },
      { id: 'archivo' as Pestana, texto: 'Cargue por archivo', permiso: 'LECTURA_CARGAR_ARCHIVO' },
      { id: 'revision' as Pestana, texto: 'En revisión', permiso: 'LECTURA_REVISAR' },
      { id: 'historial' as Pestana, texto: 'Historial', permiso: null },
    ].filter((p) => !p.permiso || this.auth.tienePermiso(p.permiso)),
  );
  readonly pestana = signal<Pestana>(this.pestanas()[0].id);
  readonly enviando = signal(false);
  readonly ultima = signal<LecturaVista | null>(null);
  readonly archivo = signal<File | null>(null);
  readonly carga = signal<ResultadoCarga | null>(null);
  readonly revision = signal<Pagina<LecturaVista> | null>(null);
  readonly enRevision = signal<LecturaVista | null>(null);
  readonly historial = signal<Pagina<LecturaVista> | null>(null);

  readonly form = this.fb.group(
    {
      serial: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{4,30}$/)]],
      periodo: [String(periodoAnterior()), [Validators.required, periodo]],
      fechaLectura: [this.hoy, [Validators.required, fechaNoFutura]],
      sinLectura: [false],
      lecturaActual: this.fb.control<number | null>(null, [Validators.required, Validators.min(0), Validators.max(999999999.999)]),
      causaNoLectura: [{ value: '', disabled: true }, Validators.required],
      observacion: ['', [Validators.maxLength(300), sinHtml]],
    },
    { validators: fechaDelPeriodo },
  );

  readonly decision = this.fb.group({
    decision: ['CONFIRMAR' as 'CONFIRMAR' | 'ESTIMAR', Validators.required],
    observacion: ['', [Validators.maxLength(300), sinHtml]],
  });

  readonly filtroHistorial = this.fb.group({ serial: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{4,30}$/)]] });

  constructor() {
    this.form.controls.sinLectura.valueChanges.subscribe((sin) => {
      const { lecturaActual, causaNoLectura } = this.form.controls;
      if (sin) {
        lecturaActual.reset(null);
        lecturaActual.disable();
        causaNoLectura.enable();
      } else {
        causaNoLectura.reset('');
        causaNoLectura.disable();
        lecturaActual.enable();
      }
    });
    // OTRA exige explicar la causa; estimar una lectura en revisión exige observación.
    this.form.controls.causaNoLectura.valueChanges.subscribe((c) => this.exigirObservacion(this.form.controls.observacion, c === 'OTRA'));
    this.decision.controls.decision.valueChanges.subscribe((d) => this.exigirObservacion(this.decision.controls.observacion, d === 'ESTIMAR'));
    this.cambiar(this.pestana());
  }

  private exigirObservacion(ctrl: AbstractControl, exigida: boolean) {
    ctrl.setValidators(exigida ? [Validators.required, Validators.minLength(5), Validators.maxLength(300), sinHtml]
      : [Validators.maxLength(300), sinHtml]);
    ctrl.updateValueAndValidity();
  }

  cambiar(p: Pestana) {
    this.pestana.set(p);
    if (p === 'revision') this.cargarRevision(0);
  }

  registrar() {
    if (!listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    this.enviando.set(true);
    this.servicio
      .registrar({
        serial: v.serial.trim().toUpperCase(),
        periodo: Number(v.periodo),
        fechaLectura: v.fechaLectura,
        lecturaActual: v.sinLectura ? null : v.lecturaActual,
        causaNoLectura: v.sinLectura ? v.causaNoLectura : null,
        observacion: v.observacion.trim() || null,
      })
      .subscribe({
        next: (l) => {
          this.enviando.set(false);
          this.ultima.set(l);
          if (l.estado === 'EN_REVISION') {
            this.avisos.info('Consumo atípico: la lectura quedó en revisión y se avisó a los asesores');
          } else {
            this.avisos.exito(`Lectura ${l.estado.toLowerCase()}: ${l.consumo ?? 0} m³`);
          }
          this.form.patchValue({ serial: '', lecturaActual: null, observacion: '', sinLectura: false });
          this.form.markAsUntouched();
        },
        error: (e: ApiError) => {
          this.enviando.set(false);
          aplicarErroresServidor(this.form, e);
        },
      });
  }

  elegir(evento: Event) {
    const archivo = (evento.target as HTMLInputElement).files?.[0] ?? null;
    if (archivo && (archivo.size === 0 || archivo.size > CARGA_MAX_BYTES || !/\.(csv|txt)$/i.test(archivo.name))) {
      this.avisos.error('Seleccione un archivo CSV de máximo 1 MB');
      this.archivo.set(null);
      return;
    }
    this.archivo.set(archivo);
  }

  cargar() {
    const archivo = this.archivo();
    if (!archivo) return;
    this.enviando.set(true);
    this.servicio.cargar(archivo).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.carga.set(r);
        this.avisos[r.conError ? 'info' : 'exito'](`${r.registradas} lecturas registradas, ${r.conError} filas con error`);
      },
      error: () => this.enviando.set(false),
    });
  }

  cargarRevision(page: number) {
    this.servicio.enRevision(null, page).subscribe((p) => this.revision.set(p));
  }

  seleccionar(l: LecturaVista) {
    this.decision.reset({ decision: 'CONFIRMAR', observacion: '' });
    this.enRevision.set(l);
  }

  revisar(l: LecturaVista) {
    if (!listoParaEnviar(this.decision)) return;
    const v = this.decision.getRawValue();
    this.servicio.revisar(l.id, v.decision, v.observacion.trim() || null).subscribe({
      next: (r) => {
        this.avisos.exito(`Lectura ${r.estado.toLowerCase()}: se facturan ${r.consumo ?? 0} m³`);
        this.enRevision.set(null);
        this.cargarRevision(this.revision()?.page.number ?? 0);
      },
      error: (e: ApiError) => aplicarErroresServidor(this.decision, e),
    });
  }

  cargarHistorial(page: number) {
    if (!listoParaEnviar(this.filtroHistorial)) return;
    const serial = this.filtroHistorial.getRawValue().serial.trim().toUpperCase();
    this.servicio.historial({ serial, page }).subscribe((p) => this.historial.set(p));
  }
}
