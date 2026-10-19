import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { CatalogosService } from '../../core/catalogos.service';
import { ApiError, Pagina, TIPOS_SERVICIO, USOS_PREDIO } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, hoyIso, listoParaEnviar } from '../../shared/formularios';
import { Paginador } from '../../shared/paginador';
import { longitudRecortada, sinHtml } from '../../shared/validadores';
import { FactorVista, NuevaVigencia, TarifaVista, TarifasService, topesFactor } from './tarifas.service';

type Pestana = 'tarifas' | 'factores';

/** Acueducto y alcantarillado exigen rangos; el gas no los admite (CK_TARIFA_RANGOS). */
function rangosTarifa(g: AbstractControl): ValidationErrors | null {
  const v = g.value as Record<string, unknown>;
  const rangos = ['valorM3Complementario', 'valorM3Suntuario', 'consumoBasicoM3', 'consumoSuntuarioDesdeM3'];
  const vacio = (k: string) => v[k] === null || v[k] === undefined || v[k] === '';
  if (v['tipoServicio'] === 'GAS') {
    return rangos.some((k) => !vacio(k)) ? { rangosGas: true } : null;
  }
  if (rangos.some(vacio)) return { rangosFaltan: true };
  return Number(v['consumoSuntuarioDesdeM3']) < Number(v['consumoBasicoM3']) ? { rangosOrden: true } : null;
}

/** Porcentaje dentro de los topes legales por uso y estrato (CK_FACTOR_TOPES). */
function topes(g: AbstractControl): ValidationErrors | null {
  const v = g.value as { uso?: string; estrato?: number | null; porcentaje?: number | null };
  if (!v.uso || v.porcentaje === null || v.porcentaje === undefined || String(v.porcentaje) === '') return null;
  const [min, max] = topesFactor(v.uso, v.estrato ?? null);
  return v.porcentaje < min || v.porcentaje > max ? { topes: { min, max } } : null;
}

/**
 * Tarifas CRA/CREG y factores de subsidio y contribución (P-08, RN-37). Nunca se editan: se cierra la vigencia
 * o se crea una nueva, que cierra la abierta el día anterior en la misma transacción.
 */
@Component({
  selector: 'app-tarifas',
  imports: [ReactiveFormsModule, CampoError, Paginador, DecimalPipe, DatePipe],
  template: `
    <h1>Tarifas y factores</h1>
    <div class="pestanas" role="tablist">
      <button type="button" role="tab" [class.activa]="pestana() === 'tarifas'" (click)="cambiar('tarifas')">Tarifas</button>
      <button type="button" role="tab" [class.activa]="pestana() === 'factores'" (click)="cambiar('factores')">Factores de subsidio y contribución</button>
    </div>

    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <select formControlName="municipioId">
        <option [ngValue]="null">Todos los municipios</option>
        @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
      </select>
      <select formControlName="tipoServicio">
        <option value="">Todos los servicios</option>
        @for (t of servicios; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
      </select>
      @if (pestana() === 'factores') {
        <select formControlName="uso">
          <option value="">Todos los usos</option>
          @for (u of usos; track u.valor) { <option [value]="u.valor">{{ u.etiqueta }}</option> }
        </select>
      }
      <label>Vigente en <input type="date" formControlName="vigenteEn" /></label>
      <button type="submit">Filtrar</button>
    </form>

    @if (pestana() === 'tarifas') {
      <table class="tabla">
        <thead>
          <tr><th>Municipio</th><th>Servicio</th><th class="num">Cargo fijo</th><th class="num">m³ básico</th><th class="num">m³ compl.</th>
            <th class="num">m³ suntuario</th><th>Rangos (m³)</th><th>Vigencia</th><th>Resolución</th><th></th></tr>
        </thead>
        <tbody>
          @for (t of tarifas()?.content ?? []; track t.id) {
            <tr>
              <td>{{ t.municipio }}</td><td>{{ t.tipoServicio }}</td>
              <td class="num">{{ t.cargoFijo | number: '1.2-2' }}</td><td class="num">{{ t.valorM3Basico | number: '1.2-4' }}</td>
              <td class="num">{{ t.valorM3Complementario | number: '1.2-4' }}</td><td class="num">{{ t.valorM3Suntuario | number: '1.2-4' }}</td>
              <td>{{ t.consumoBasicoM3 ? 'básico ≤ ' + t.consumoBasicoM3 + ' · suntuario > ' + t.consumoSuntuarioDesdeM3 : '—' }}</td>
              <td>
                {{ t.vigenteDesde | date: 'mediumDate' }} – {{ t.vigenteHasta ? (t.vigenteHasta | date: 'mediumDate') : 'abierta' }}
                @if (t.vigenteHoy) { <span class="etiqueta">Vigente</span> }
              </td>
              <td>{{ t.resolucion }}</td>
              <td>@if (!t.vigenteHasta && puedeGestionar) { <button type="button" class="enlace" (click)="abrirCierre(t)">Cerrar</button> }</td>
            </tr>
          } @empty { <tr><td colspan="10">Sin tarifas.</td></tr> }
        </tbody>
      </table>
      <app-paginador [pagina]="tarifas()" (cambiar)="buscar($event)" />
    } @else {
      <table class="tabla">
        <thead><tr><th>Municipio</th><th>Servicio</th><th>Uso</th><th>Estrato</th><th class="num">Porcentaje</th><th>Vigencia</th><th>Acuerdo</th><th></th></tr></thead>
        <tbody>
          @for (f of factores()?.content ?? []; track f.id) {
            <tr>
              <td>{{ f.municipio }}</td><td>{{ f.tipoServicio }}</td><td>{{ f.uso }}</td><td>{{ f.estrato ?? '—' }}</td>
              <td class="num">{{ f.porcentaje | number: '1.0-2' }} % {{ f.porcentaje < 0 ? '(subsidio)' : f.porcentaje > 0 ? '(contribución)' : '' }}</td>
              <td>
                {{ f.vigenteDesde | date: 'mediumDate' }} – {{ f.vigenteHasta ? (f.vigenteHasta | date: 'mediumDate') : 'abierto' }}
                @if (f.vigenteHoy) { <span class="etiqueta">Vigente</span> }
              </td>
              <td>{{ f.acuerdoMunicipal }}</td>
              <td>@if (!f.vigenteHasta && puedeGestionar) { <button type="button" class="enlace" (click)="abrirCierre(f)">Cerrar</button> }</td>
            </tr>
          } @empty { <tr><td colspan="8">Sin factores.</td></tr> }
        </tbody>
      </table>
      <app-paginador [pagina]="factores()" (cambiar)="buscar($event)" />
    }

    @if (cierre(); as c) {
      <form class="tarjeta" [formGroup]="formCierre" (ngSubmit)="cerrar()" novalidate>
        <h3>Cerrar vigencia de {{ c.municipio }} · {{ c.tipoServicio }}</h3>
        <label>Último día de vigencia <input type="date" formControlName="vigenteHasta" [min]="c.vigenteDesde" /></label>
        <app-campo-error [control]="formCierre.controls.vigenteHasta" />
        <p class="ayuda">Mientras no haya una nueva vigencia, la facturación de periodos posteriores no tendrá tarifa.</p>
        <div class="acciones">
          <button type="submit" class="peligro">Cerrar vigencia</button>
          <button type="button" class="secundario" (click)="cierre.set(null)">Cancelar</button>
        </div>
      </form>
    }

    @if (puedeGestionar) {
      @if (pestana() === 'tarifas') {
        <details>
          <summary>Registrar tarifa</summary>
          <form class="tarjeta" [formGroup]="formTarifa" (ngSubmit)="guardarTarifa()" novalidate>
            <div class="fila">
              <label>Municipio
                <select formControlName="municipioId">
                  <option [ngValue]="null" disabled>Seleccione</option>
                  @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
                </select>
                <app-campo-error [control]="formTarifa.controls.municipioId" />
              </label>
              <label>Servicio
                <select formControlName="tipoServicio">
                  @for (t of servicios; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
                </select>
              </label>
              <label>Vigente desde <input type="date" formControlName="vigenteDesde" />
                <app-campo-error [control]="formTarifa.controls.vigenteDesde" /></label>
            </div>
            <div class="fila">
              <label>Cargo fijo ($) <input type="number" formControlName="cargoFijo" min="0" step="0.01" />
                <app-campo-error [control]="formTarifa.controls.cargoFijo" /></label>
              <label>Valor m³ básico ($) <input type="number" formControlName="valorM3Basico" min="0" step="0.0001" />
                <app-campo-error [control]="formTarifa.controls.valorM3Basico" /></label>
            </div>
            @if (formTarifa.controls.tipoServicio.value !== 'GAS') {
              <div class="fila">
                <label>Valor m³ complementario <input type="number" formControlName="valorM3Complementario" min="0" step="0.0001" /></label>
                <label>Valor m³ suntuario <input type="number" formControlName="valorM3Suntuario" min="0" step="0.0001" /></label>
                <label>Consumo básico hasta (m³) <input type="number" formControlName="consumoBasicoM3" min="0" step="0.01" /></label>
                <label>Suntuario desde (m³) <input type="number" formControlName="consumoSuntuarioDesdeM3" min="0" step="0.01" /></label>
              </div>
            }
            @if (formTarifa.errors?.['rangosFaltan']) { <small class="error">Acueducto y alcantarillado requieren los cuatro valores de rangos</small> }
            @if (formTarifa.errors?.['rangosOrden']) { <small class="error">El suntuario debe empezar en o después del límite del básico</small> }
            <label>Resolución <input formControlName="resolucion" maxlength="80" placeholder="Resolución CRA 123 de 2026" /></label>
            <app-campo-error [control]="formTarifa.controls.resolucion" />
            <label class="check"><input type="checkbox" formControlName="nuevaVigencia" />
              Reemplaza la tarifa abierta (la cierra el día anterior a esta vigencia)</label>
            <button type="submit" [disabled]="enviando()">Guardar tarifa</button>
          </form>
        </details>
      } @else {
        <details>
          <summary>Registrar factor</summary>
          <form class="tarjeta" [formGroup]="formFactor" (ngSubmit)="guardarFactor()" novalidate>
            <div class="fila">
              <label>Municipio
                <select formControlName="municipioId">
                  <option [ngValue]="null" disabled>Seleccione</option>
                  @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
                </select>
                <app-campo-error [control]="formFactor.controls.municipioId" />
              </label>
              <label>Servicio
                <select formControlName="tipoServicio">
                  <option value="ACUEDUCTO">Acueducto</option>
                  <option value="ALCANTARILLADO">Alcantarillado</option>
                </select>
              </label>
              <label>Uso
                <select formControlName="uso">
                  @for (u of usos; track u.valor) { <option [value]="u.valor">{{ u.etiqueta }}</option> }
                </select>
              </label>
              @if (formFactor.controls.uso.value === 'RESIDENCIAL') {
                <label>Estrato
                  <select formControlName="estrato">
                    <option [ngValue]="null" disabled>Seleccione</option>
                    @for (e of [1, 2, 3, 4, 5, 6]; track e) { <option [ngValue]="e">{{ e }}</option> }
                  </select>
                  <app-campo-error [control]="formFactor.controls.estrato" />
                </label>
              }
            </div>
            <div class="fila">
              <label>Porcentaje (negativo = subsidio) <input type="number" formControlName="porcentaje" step="0.01" />
                <app-campo-error [control]="formFactor.controls.porcentaje" /></label>
              <label>Vigente desde <input type="date" formControlName="vigenteDesde" />
                <app-campo-error [control]="formFactor.controls.vigenteDesde" /></label>
            </div>
            @if (formFactor.errors?.['topes']; as t) {
              <small class="error">Para ese uso y estrato el porcentaje va de {{ t.min }} a {{ t.max }}</small>
            }
            <label>Acuerdo municipal <input formControlName="acuerdoMunicipal" maxlength="80" /></label>
            <app-campo-error [control]="formFactor.controls.acuerdoMunicipal" />
            <label class="check"><input type="checkbox" formControlName="nuevaVigencia" />
              Reemplaza el factor abierto (lo cierra el día anterior a esta vigencia)</label>
            <button type="submit" [disabled]="enviando()">Guardar factor</button>
          </form>
        </details>
      }
    }
  `,
})
export class Tarifas {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(TarifasService);
  private readonly avisos = inject(AvisosService);
  readonly puedeGestionar = inject(AuthService).tienePermiso('TARIFA_GESTIONAR');
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly servicios = TIPOS_SERVICIO;
  readonly usos = USOS_PREDIO;

  readonly pestana = signal<Pestana>('tarifas');
  readonly tarifas = signal<Pagina<TarifaVista> | null>(null);
  readonly factores = signal<Pagina<FactorVista> | null>(null);
  readonly cierre = signal<TarifaVista | FactorVista | null>(null);
  readonly enviando = signal(false);

  readonly filtros = this.fb.group({
    municipioId: this.fb.control<number | null>(null),
    tipoServicio: [''],
    uso: [''],
    vigenteEn: [''],
  });

  readonly formTarifa = this.fb.group(
    {
      municipioId: this.fb.control<number | null>(null, Validators.required),
      tipoServicio: ['ACUEDUCTO', Validators.required],
      cargoFijo: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
      valorM3Basico: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
      valorM3Complementario: this.fb.control<number | null>(null, Validators.min(0)),
      valorM3Suntuario: this.fb.control<number | null>(null, Validators.min(0)),
      consumoBasicoM3: this.fb.control<number | null>(null, Validators.min(0.01)),
      consumoSuntuarioDesdeM3: this.fb.control<number | null>(null, Validators.min(0.01)),
      vigenteDesde: [hoyIso(), Validators.required],
      resolucion: ['', [Validators.required, longitudRecortada(5, 80), sinHtml]],
      nuevaVigencia: [true],
    },
    { validators: rangosTarifa },
  );

  readonly formFactor = this.fb.group(
    {
      municipioId: this.fb.control<number | null>(null, Validators.required),
      tipoServicio: ['ACUEDUCTO', Validators.required],
      uso: ['RESIDENCIAL', Validators.required],
      estrato: this.fb.control<number | null>(null, Validators.required),
      porcentaje: this.fb.control<number | null>(null, [Validators.required, Validators.min(-100), Validators.max(100)]),
      vigenteDesde: [hoyIso(), Validators.required],
      acuerdoMunicipal: ['', [Validators.required, longitudRecortada(5, 80), sinHtml]],
      nuevaVigencia: [true],
    },
    { validators: topes },
  );

  readonly formCierre = this.fb.group({ vigenteHasta: [hoyIso(), Validators.required] });

  constructor() {
    this.formTarifa.controls.tipoServicio.valueChanges.subscribe((t) => {
      if (t === 'GAS') {
        this.formTarifa.patchValue({ valorM3Complementario: null, valorM3Suntuario: null, consumoBasicoM3: null, consumoSuntuarioDesdeM3: null });
      }
    });
    this.formFactor.controls.uso.valueChanges.subscribe((uso) => {
      const estrato = this.formFactor.controls.estrato;
      if (uso === 'RESIDENCIAL') {
        estrato.enable();
      } else {
        estrato.setValue(null);
        estrato.disable();
      }
    });
    this.buscar(0);
  }

  cambiar(p: Pestana) {
    this.pestana.set(p);
    this.cierre.set(null);
    this.buscar(0);
  }

  buscar(page: number) {
    const v = this.filtros.getRawValue();
    if (this.pestana() === 'tarifas') {
      this.servicio.tarifas({ ...v, uso: undefined, page }).subscribe((p) => this.tarifas.set(p));
    } else {
      this.servicio.factores({ ...v, page }).subscribe((p) => this.factores.set(p));
    }
  }

  abrirCierre(r: TarifaVista | FactorVista) {
    this.formCierre.reset({ vigenteHasta: hoyIso() });
    this.cierre.set(r);
  }

  cerrar() {
    const r = this.cierre();
    if (!r || !listoParaEnviar(this.formCierre)) return;
    const hasta = this.formCierre.getRawValue().vigenteHasta;
    const op: Observable<unknown> = this.pestana() === 'tarifas'
      ? this.servicio.cerrarTarifa(r.id, hasta, r.version)
      : this.servicio.cerrarFactor(r.id, hasta, r.version);
    op.subscribe({
      next: () => {
        this.avisos.exito('Vigencia cerrada');
        this.cierre.set(null);
        this.buscar(0);
      },
      error: (e: ApiError) => aplicarErroresServidor(this.formCierre, e),
    });
  }

  guardarTarifa() {
    if (!listoParaEnviar(this.formTarifa)) return;
    const { nuevaVigencia, ...v } = this.formTarifa.getRawValue();
    const datos = { ...v, municipioId: v.municipioId!, cargoFijo: v.cargoFijo!, valorM3Basico: v.valorM3Basico!, resolucion: v.resolucion.trim() };
    this.enviar(nuevaVigencia ? this.servicio.nuevaVigenciaTarifa(datos) : this.servicio.crearTarifa(datos), this.formTarifa);
  }

  guardarFactor() {
    if (!listoParaEnviar(this.formFactor)) return;
    const { nuevaVigencia, ...v } = this.formFactor.getRawValue();
    const datos = {
      ...v,
      municipioId: v.municipioId!,
      porcentaje: v.porcentaje!,
      estrato: v.uso === 'RESIDENCIAL' ? v.estrato : null,
      acuerdoMunicipal: v.acuerdoMunicipal.trim(),
    };
    this.enviar(nuevaVigencia ? this.servicio.nuevaVigenciaFactor(datos) : this.servicio.crearFactor(datos), this.formFactor);
  }

  private enviar(op: Observable<NuevaVigencia<unknown> | unknown>, form: typeof this.formTarifa | typeof this.formFactor) {
    this.enviando.set(true);
    op.subscribe({
      next: (r) => {
        this.enviando.set(false);
        const cerrada = r && typeof r === 'object' && 'cerrada' in r && r.cerrada;
        this.avisos.exito(cerrada ? 'Nueva vigencia registrada; la anterior quedó cerrada el día previo' : 'Vigencia registrada');
        this.buscar(0);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        aplicarErroresServidor(form as never, e);
      },
    });
  }
}
