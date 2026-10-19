import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { AcuerdoDatos, CarteraService, DeudaUsuario, SimulacionAcuerdo } from './cartera.service';

/**
 * Acuerdo de pago (P-12, RN-30, RN-33, RN-38): solo facturas VENCIDAS sin valores en reclamo; cuota inicial de al
 * menos ACUERDO_CUOTA_INICIAL_PCT; 2 a ACUERDO_MAX_CUOTAS cuotas; tasa hasta el tope del uso del predio y la usura.
 * Siempre se simula antes de firmar; la simulación se invalida si cambia cualquier dato.
 */
@Component({
  selector: 'app-nuevo-acuerdo',
  imports: [ReactiveFormsModule, CampoError, CurrencyPipe, DatePipe, DecimalPipe, RouterLink],
  template: `
    <p><a routerLink="/cartera">← Cartera</a></p>
    <h1>Nuevo acuerdo de pago</h1>
    @if (deuda(); as d) {
      <p>{{ d.codigoUsuario }} · {{ d.nombre }}</p>
      @if (d.acuerdoVigente) {
        <p class="aviso-inline peligro">El usuario ya tiene el acuerdo vigente {{ d.acuerdoVigente }}.</p>
      }
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="simular()" novalidate>
        <h2>Facturas vencidas</h2>
        @for (f of d.facturas; track f.id) {
          <label class="check">
            <input type="checkbox" [checked]="seleccion().has(f.id)" [disabled]="!f.incluible" (change)="alternar(f.id)" />
            {{ f.numeroFactura }} · periodo {{ f.periodo }} · {{ f.municipio }} ({{ f.uso }}) · saldo {{ f.saldoPendiente | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}
            @if (f.valorEnReclamo > 0) { <span class="etiqueta">En reclamo: no se financia</span> }
          </label>
        } @empty { <p>El usuario no tiene facturas vencidas (RN-30).</p> }
        <p class="total">Deuda seleccionada: <strong>{{ total() | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</strong></p>
        <div class="fila">
          <label>Cuota inicial (mínimo {{ minimo() | currency: 'COP' : 'symbol-narrow' : '1.0-2' }})
            <input type="number" formControlName="cuotaInicial" min="0" step="0.01" />
            <app-campo-error [control]="form.controls.cuotaInicial" />
          </label>
          <label>Número de cuotas ({{ d.minCuotas }} a {{ d.maxCuotas }})
            <input type="number" formControlName="numeroCuotas" [min]="d.minCuotas" [max]="d.maxCuotas" step="1" />
            <app-campo-error [control]="form.controls.numeroCuotas" />
          </label>
          <label>Tasa efectiva anual % (máximo {{ d.tasaMaxima | number: '1.0-4' }})
            <input type="number" formControlName="tasaInteresAnual" min="0" [max]="d.tasaMaxima" step="0.01" />
            <app-campo-error [control]="form.controls.tasaInteresAnual" />
          </label>
        </div>
        @if (errorMonto()) { <small class="error" role="alert">{{ errorMonto() }}</small> }
        <button type="submit" class="secundario" [disabled]="!seleccion().size || !!d.acuerdoVigente">Simular</button>
      </form>
    } @else {
      <p class="cargando">Cargando deuda…</p>
    }

    @if (simulacion(); as s) {
      <section class="tarjeta">
        <h2>Plan de pagos</h2>
        <dl class="ficha">
          <dt>Deuda</dt><dd>{{ s.totalDeuda | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
          <dt>Cuota inicial</dt><dd>{{ s.cuotaInicial | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
          <dt>Saldo financiado</dt><dd>{{ s.saldoFinanciado | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
          <dt>Tasa</dt><dd>{{ s.tasaInteresAnual | number: '1.0-4' }} % E.A. ({{ s.tasaInteresMensual * 100 | number: '1.0-6' }} % mensual)</dd>
          <dt>Cuota mensual</dt><dd>{{ s.cuotaFija | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
          <dt>Intereses</dt><dd>{{ s.totalIntereses | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</dd>
          <dt>Total a pagar</dt><dd><strong>{{ s.totalAPagar | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</strong></dd>
        </dl>
        <table class="tabla">
          <thead><tr><th>Cuota</th><th>Vence</th><th>Capital</th><th>Interés</th><th>Valor</th><th>Saldo</th></tr></thead>
          <tbody>
            @for (c of s.cuotas; track c.numeroCuota) {
              <tr><td>{{ c.numeroCuota === 0 ? 'Inicial' : c.numeroCuota }}</td><td>{{ c.fechaVencimiento | date: 'mediumDate' }}</td>
                <td class="num">{{ c.valorCapital | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                <td class="num">{{ c.valorInteres | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                <td class="num">{{ c.valorCuota | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td>
                <td class="num">{{ c.saldoDespues | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</td></tr>
            }
          </tbody>
        </table>
        <p class="ayuda">Si una cuota no se paga a tiempo el acuerdo queda incumplido y la deuda vuelve a cobro (RN-07).</p>
        <button type="button" (click)="crear()" [disabled]="enviando()">Firmar acuerdo</button>
      </section>
    }
  `,
})
export class NuevoAcuerdo implements OnInit {
  private readonly servicio = inject(CarteraService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly usuarioId = input.required<string>();
  readonly deuda = signal<DeudaUsuario | null>(null);
  readonly seleccion = signal(new Set<number>());
  readonly simulacion = signal<SimulacionAcuerdo | null>(null);
  readonly enviando = signal(false);

  readonly form = this.fb.group({
    cuotaInicial: [0, [Validators.required, Validators.min(0.01)]],
    numeroCuotas: [6, [Validators.required, Validators.min(2), Validators.max(24), Validators.pattern(/^\d+$/)]],
    tasaInteresAnual: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
  });

  readonly total = computed(() =>
    Math.round((this.deuda()?.facturas ?? []).filter((f) => this.seleccion().has(f.id)).reduce((s, f) => s + f.saldoPendiente, 0) * 100) / 100,
  );
  /** Igual que el trigger: ROUND(deuda × pct / 100, 2). */
  readonly minimo = computed(() => Math.round(this.total() * (this.deuda()?.cuotaInicialPct ?? 10)) / 100);
  private readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly errorMonto = computed(() => {
    const v = this.valores();
    const ci = Number(v.cuotaInicial);
    const d = this.deuda();
    if (!this.total() || !d) return null;
    if (ci < this.minimo()) return `La cuota inicial debe ser al menos el ${d.cuotaInicialPct} % de la deuda`;
    if (ci >= this.total()) return 'La cuota inicial debe ser menor que la deuda';
    if (Number(v.numeroCuotas) > d.maxCuotas) return `Máximo ${d.maxCuotas} cuotas`;
    if (Number(v.tasaInteresAnual) > d.tasaMaxima) return `La tasa máxima aplicable es ${d.tasaMaxima} % (RN-38)`;
    return null;
  });

  constructor() {
    this.form.valueChanges.subscribe(() => this.simulacion.set(null));
  }

  ngOnInit() {
    this.servicio.deuda(Number(this.usuarioId())).subscribe((d) => {
      this.deuda.set(d);
      this.seleccion.set(new Set(d.facturas.filter((f) => f.incluible).map((x) => x.id)));
      this.form.patchValue({ cuotaInicial: this.minimo(), tasaInteresAnual: d.tasaMaxima, numeroCuotas: Math.min(6, d.maxCuotas) });
    });
  }

  alternar(id: number) {
    const s = new Set(this.seleccion());
    if (s.has(id)) {
      s.delete(id);
    } else {
      s.add(id);
    }
    this.seleccion.set(s);
    this.simulacion.set(null);
    this.form.controls.cuotaInicial.setValue(this.minimo());
  }

  private datos(): AcuerdoDatos {
    const v = this.form.getRawValue();
    return {
      usuarioId: Number(this.usuarioId()),
      facturaIds: [...this.seleccion()],
      cuotaInicial: Number(v.cuotaInicial),
      numeroCuotas: Number(v.numeroCuotas),
      tasaInteresAnual: Number(v.tasaInteresAnual),
    };
  }

  simular() {
    if (!listoParaEnviar(this.form) || this.errorMonto()) return;
    this.servicio.simular(this.datos()).subscribe({
      next: (s) => this.simulacion.set(s),
      error: (e: ApiError) => aplicarErroresServidor(this.form, e),
    });
  }

  crear() {
    const s = this.simulacion();
    if (!s || !window.confirm(`¿Firmar el acuerdo por ${s.totalDeuda} en ${s.numeroCuotas} cuotas más la inicial?`)) return;
    this.enviando.set(true);
    this.servicio.crear(this.datos()).subscribe({
      next: (a) => {
        this.avisos.exito(`Acuerdo ${a.acuerdo.numeroAcuerdo} creado. Se suspendió el cobro persuasivo.`);
        this.router.navigate(['/cartera/acuerdos', a.acuerdo.id]);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        aplicarErroresServidor(this.form, e);
      },
    });
  }
}
