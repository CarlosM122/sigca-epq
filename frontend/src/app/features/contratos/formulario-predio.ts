import { Component, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AvisosService } from '../../core/avisos.service';
import { CatalogosService } from '../../core/catalogos.service';
import { ApiError, USOS_PREDIO } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { longitudRecortada, sinHtml } from '../../shared/validadores';
import { PredioVista, Sector, UsuariosService } from '../usuarios/usuarios.service';

/**
 * Predio (P-01, P-02): ficha catastral IGAC opcional de 30 dígitos; el sector debe ser del mismo municipio;
 * el estrato es obligatorio solo para uso residencial y no aplica a los demás usos.
 */
@Component({
  selector: 'app-formulario-predio',
  imports: [ReactiveFormsModule, CampoError],
  template: `
    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <label>Dirección <input formControlName="direccion" maxlength="200" /></label>
      <app-campo-error [control]="form.controls.direccion" />
      <div class="fila">
        <label>Barrio o vereda <input formControlName="barrioVereda" maxlength="100" />
          <app-campo-error [control]="form.controls.barrioVereda" />
        </label>
        <label>Municipio
          <select formControlName="municipioId">
            <option [ngValue]="null" disabled>Seleccione</option>
            @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
          </select>
          <app-campo-error [control]="form.controls.municipioId" />
        </label>
        <label>Sector (opcional)
          <select formControlName="sectorId">
            <option [ngValue]="null">Sin sector</option>
            @for (s of sectores(); track s.id) { <option [ngValue]="s.id">{{ s.nombre }} ({{ s.tipo }})</option> }
          </select>
          <app-campo-error [control]="form.controls.sectorId" />
        </label>
      </div>
      <div class="fila">
        <label>Uso
          <select formControlName="uso">
            @for (u of usos; track u.valor) { <option [value]="u.valor">{{ u.etiqueta }}</option> }
          </select>
        </label>
        @if (form.controls.uso.value === 'RESIDENCIAL') {
          <label>Estrato
            <select formControlName="estrato">
              <option [ngValue]="null" disabled>Seleccione</option>
              @for (e of estratos; track e) { <option [ngValue]="e">{{ e }}</option> }
            </select>
            <app-campo-error [control]="form.controls.estrato" />
          </label>
        }
        <label>Ficha catastral (opcional, 30 dígitos)
          <input formControlName="fichaCatastral" inputmode="numeric" maxlength="30" />
          <app-campo-error [control]="form.controls.fichaCatastral" />
        </label>
      </div>
      <button type="submit" [disabled]="enviando()">{{ predio() ? 'Guardar cambios' : 'Registrar predio' }}</button>
    </form>
  `,
})
export class FormularioPredio {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(UsuariosService);
  private readonly avisos = inject(AvisosService);

  /** Si llega, el formulario edita ese predio. */
  readonly predio = input<PredioVista | null>(null);
  readonly guardado = output<PredioVista>();

  readonly usos = USOS_PREDIO;
  readonly estratos = [1, 2, 3, 4, 5, 6];
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly sectores = signal<Sector[]>([]);
  readonly enviando = signal(false);

  readonly form = this.fb.group({
    direccion: ['', [Validators.required, longitudRecortada(5, 200), sinHtml]],
    barrioVereda: ['', [Validators.maxLength(100), sinHtml]],
    municipioId: this.fb.control<number | null>(null, Validators.required),
    sectorId: this.fb.control<number | null>(null),
    uso: ['RESIDENCIAL', Validators.required],
    estrato: this.fb.control<number | null>(null, Validators.required),
    fichaCatastral: ['', Validators.pattern(/^\d{30}$/)],
  });

  constructor() {
    this.form.controls.uso.valueChanges.subscribe((uso) => {
      const estrato = this.form.controls.estrato;
      if (uso === 'RESIDENCIAL') {
        estrato.enable();
      } else {
        estrato.setValue(null);
        estrato.disable();
      }
    });
    this.form.controls.municipioId.valueChanges.subscribe((m) => this.cargarSectores(m, null));
  }

  ngOnInit() {
    const p = this.predio();
    if (p) {
      this.form.patchValue({ ...p, barrioVereda: p.barrioVereda ?? '', fichaCatastral: p.fichaCatastral ?? '' }, { emitEvent: false });
      if (p.uso !== 'RESIDENCIAL') this.form.controls.estrato.disable();
      this.cargarSectores(p.municipioId, p.sectorId ?? null);
    }
  }

  private cargarSectores(municipioId: number | null, seleccion: number | null) {
    this.form.controls.sectorId.setValue(seleccion, { emitEvent: false });
    this.sectores.set([]);
    if (municipioId) {
      this.servicio.sectores(municipioId).subscribe((s) => this.sectores.set(s));
    }
  }

  guardar() {
    if (!listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    const actual = this.predio();
    const datos = {
      direccion: v.direccion.trim(),
      barrioVereda: v.barrioVereda.trim() || null,
      municipioId: v.municipioId!,
      sectorId: v.sectorId,
      uso: v.uso,
      estrato: v.uso === 'RESIDENCIAL' ? v.estrato : null,
      fichaCatastral: v.fichaCatastral || null,
      version: actual?.version ?? null,
    };
    this.enviando.set(true);
    const op = actual ? this.servicio.modificarPredio(actual.id, datos) : this.servicio.crearPredio(datos);
    op.subscribe({
      next: (p) => {
        this.enviando.set(false);
        this.avisos.exito(actual ? `Predio ${p.codigoPredio} actualizado` : `Predio ${p.codigoPredio} registrado`);
        if (!actual) {
          this.form.reset();
          this.sectores.set([]);
        }
        this.guardado.emit(p);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        aplicarErroresServidor(this.form, e);
        if (e.codigo === 'SECTOR_INVALIDO') this.form.controls.sectorId.setErrors({ servidor: e.mensaje });
        if (e.codigo === 'DUPLICADO') this.form.controls.fichaCatastral.setErrors({ servidor: 'Ya existe un predio con esa ficha catastral' });
      },
    });
  }
}
