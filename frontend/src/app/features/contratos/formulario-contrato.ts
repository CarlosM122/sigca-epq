import { Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AvisosService } from '../../core/avisos.service';
import { ApiError, TIPOS_SERVICIO } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, hoyIso, listoParaEnviar } from '../../shared/formularios';
import { fechaNoFutura, sinHtml } from '../../shared/validadores';
import { PredioVista, UsuariosService } from '../usuarios/usuarios.service';
import { ContratoVista, ContratosService, MedidorVista } from './contratos.service';
import { FormularioPredio } from './formulario-predio';

/**
 * Contrato (RF-04, P-04, P-06, RN-01): uno vigente por usuario, servicio y predio. Acueducto y gas usan el
 * medidor activo del predio; alcantarillado no lleva medidor y exige un contrato de acueducto vigente.
 */
@Component({
  selector: 'app-formulario-contrato',
  imports: [ReactiveFormsModule, CampoError, FormularioPredio, RouterLink],
  template: `
    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <fieldset>
        <legend>Predio</legend>
        <div class="filtros">
          <input [formControl]="textoPredio" placeholder="Buscar predio por código o dirección" maxlength="100" />
          <button type="button" class="secundario" (click)="buscarPredios()">Buscar</button>
        </div>
        <label>Predio
          <select formControlName="predioId">
            <option [ngValue]="null" disabled>Seleccione</option>
            @for (p of opciones(); track p.id) {
              <option [ngValue]="p.id">{{ p.codigoPredio }} · {{ p.direccion }} · {{ p.municipio }}</option>
            }
          </select>
          <app-campo-error [control]="form.controls.predioId" />
        </label>
        <details>
          <summary>Registrar un predio nuevo</summary>
          <app-formulario-predio (guardado)="predioCreado($event)" />
        </details>
      </fieldset>
      <div class="fila">
        <label>Servicio
          <select formControlName="tipoServicio">
            @for (t of servicios; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
          </select>
        </label>
        <label>Fecha de vinculación
          <input type="date" formControlName="fechaVinculacion" [max]="hoy" />
          <app-campo-error [control]="form.controls.fechaVinculacion" />
        </label>
        @if (tipoServicio() !== 'ALCANTARILLADO' && predioId()) {
          <label>Medidor
            <select formControlName="medidorId">
              @for (m of medidoresDelServicio(); track m.id) { <option [ngValue]="m.id">{{ m.serial }}</option> }
            </select>
          </label>
        }
      </div>
      @if (tipoServicio() !== 'ALCANTARILLADO' && predioId() && !medidoresDelServicio().length) {
        <p class="aviso-inline">El predio no tiene un medidor activo de {{ tipoServicio().toLowerCase() }}.
          <a [routerLink]="['/contratos/predios', predioId()]">Instálelo en la ficha del predio</a>.</p>
      }
      @if (conflicto(); as m) { <p class="error" role="alert">{{ m }}</p> }
      <button type="submit" [disabled]="enviando() || !!conflicto()">Registrar contrato</button>
    </form>
  `,
})
export class FormularioContrato {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(ContratosService);
  private readonly usuarios = inject(UsuariosService);
  private readonly avisos = inject(AvisosService);

  readonly usuarioId = input.required<number>();
  readonly predios = input<PredioVista[]>([]);
  readonly contratos = input<ContratoVista[]>([]);
  readonly creado = output<ContratoVista>();

  readonly servicios = TIPOS_SERVICIO;
  readonly hoy = hoyIso();
  readonly enviando = signal(false);
  readonly encontrados = signal<PredioVista[]>([]);
  readonly medidores = signal<MedidorVista[]>([]);
  readonly textoPredio = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100), sinHtml] });

  readonly form = this.fb.group({
    predioId: this.fb.control<number | null>(null, Validators.required),
    tipoServicio: ['ACUEDUCTO', Validators.required],
    medidorId: this.fb.control<number | null>(null),
    fechaVinculacion: [this.hoy, [Validators.required, fechaNoFutura]],
  });

  readonly tipoServicio = toSignal(this.form.controls.tipoServicio.valueChanges, { initialValue: 'ACUEDUCTO' });
  readonly predioId = toSignal(this.form.controls.predioId.valueChanges, { initialValue: null });

  /** Predios del usuario más los encontrados en la búsqueda, sin repetir. */
  readonly opciones = computed(() => {
    const vistos = new Map<number, PredioVista>();
    [...this.predios(), ...this.encontrados()].forEach((p) => vistos.set(p.id, p));
    return [...vistos.values()];
  });

  readonly medidoresDelServicio = computed(() =>
    this.medidores().filter((m) => m.estado === 'ACTIVO' && m.tipoServicio === this.tipoServicio()),
  );

  /** Anticipa RN-01 y P-06 con los contratos ya cargados; el backend y Oracle lo vuelven a validar. */
  readonly conflicto = computed(() => {
    const predio = this.predioId();
    const tipo = this.tipoServicio();
    if (!predio) return null;
    const vigentes = this.contratos().filter((c) => c.predioId === predio && c.estado !== 'CANCELADO');
    if (vigentes.some((c) => c.tipoServicio === tipo)) {
      return 'El usuario ya tiene un contrato vigente de este servicio en el predio';
    }
    if (tipo === 'ALCANTARILLADO' && !vigentes.some((c) => c.tipoServicio === 'ACUEDUCTO')) {
      return 'Alcantarillado requiere un contrato de acueducto vigente del usuario en el mismo predio';
    }
    return null;
  });

  constructor() {
    this.form.controls.predioId.valueChanges.subscribe((id) => {
      this.medidores.set([]);
      if (id) this.servicio.medidores(id).subscribe((m) => {
        this.medidores.set(m);
        this.elegirMedidor();
      });
    });
    this.form.controls.tipoServicio.valueChanges.subscribe(() => queueMicrotask(() => this.elegirMedidor()));
  }

  private elegirMedidor() {
    this.form.controls.medidorId.setValue(this.medidoresDelServicio()[0]?.id ?? null);
  }

  buscarPredios() {
    const texto = this.textoPredio.value.trim();
    if (this.textoPredio.invalid || texto.length < 3) {
      this.avisos.info('Escriba al menos 3 caracteres');
      return;
    }
    this.usuarios.buscarPredios({ texto }).subscribe((p) => {
      this.encontrados.set(p.content);
      if (!p.content.length) this.avisos.info('No se encontraron predios');
    });
  }

  predioCreado(p: PredioVista) {
    this.encontrados.update((l) => [p, ...l]);
    this.form.controls.predioId.setValue(p.id);
  }

  guardar() {
    if (!listoParaEnviar(this.form) || this.conflicto()) return;
    const v = this.form.getRawValue();
    this.enviando.set(true);
    this.servicio
      .crear({
        usuarioId: this.usuarioId(),
        predioId: v.predioId!,
        tipoServicio: v.tipoServicio,
        medidorId: v.tipoServicio === 'ALCANTARILLADO' ? null : v.medidorId,
        fechaVinculacion: v.fechaVinculacion,
      })
      .subscribe({
        next: (c) => {
          this.enviando.set(false);
          this.avisos.exito(`Contrato ${c.numeroContrato} registrado`);
          this.creado.emit(c);
        },
        error: (e: ApiError) => {
          this.enviando.set(false);
          aplicarErroresServidor(this.form, e);
        },
      });
  }
}
