import { CurrencyPipe, DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';

import { API, params } from '../../core/api';
import { AuthService } from '../../core/auth.service';
import { CatalogosService } from '../../core/catalogos.service';
import { Icono } from '../../shared/icono';

export interface CarteraPorEdad {
  hasta30: number;
  de31a60: number;
  de61a90: number;
  mas90: number;
}

export interface Indicadores {
  facturado: number;
  facturas: number;
  recaudado: number;
  porcentajeRecaudo: number;
  recaudoMes: number;
  carteraVencida: number;
  carteraPorEdad: CarteraPorEdad;
  usuariosEnMora: number;
  acuerdosVigentes: number;
  pqrAbiertas: number;
  pqrVencidas: number;
  pqrAbiertasPorTipo: Record<string, number>;
  pqrRespondidas12m: number;
  tiempoMedioRespuestaDias: number | null;
  respuestasExtemporaneas12m: number;
  silenciosSinReconocer: number;
  contratosActivos: number;
  contratosSuspendidos: number;
  lecturasEnRevision: number;
}

export interface Tablero {
  periodo: number;
  municipioId: number | null;
  generadoEn: string;
  refrescoSegundos: number;
  global: Indicadores;
  porMunicipio: { municipioId: number; municipio: string; indicadores: Indicadores }[];
  tendencia: { periodo: number; facturado: number; recaudado: number }[];
}

const EDADES: { clave: keyof CarteraPorEdad; etiqueta: string }[] = [
  { clave: 'hasta30', etiqueta: '0 a 30 días' },
  { clave: 'de31a60', etiqueta: '31 a 60 días' },
  { clave: 'de61a90', etiqueta: '61 a 90 días' },
  { clave: 'mas90', etiqueta: 'Más de 90 días' },
];

/**
 * Tablero gerencial (RF-24, P-28): indicadores globales y por municipio, cartera por edad, PQR y tendencia de
 * 12 periodos. Se refresca cada DASHBOARD_REFRESCO_SEG segundos (lo informa el servidor en refrescoSegundos).
 */
@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DecimalPipe, DatePipe, KeyValuePipe, ReactiveFormsModule, Icono],
  template: `
    <div class="encabezado">
      <div>
        <h1>Dashboard gerencial</h1>
        <p class="ayuda">Facturación, recaudo, cartera y atención de PQR en un vistazo.</p>
      </div>
    </div>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="cargar()">
      <input formControlName="periodo" placeholder="Periodo (AAAAMM)" maxlength="6" inputmode="numeric" aria-label="Periodo" />
      @if (!auth.sesion()?.municipioId) {
        <select formControlName="municipioId" aria-label="Municipio">
          <option [ngValue]="null">Todos los municipios</option>
          @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
        </select>
      }
      <button type="submit">Consultar</button>
    </form>
    @if (error()) { <p class="aviso-inline peligro">{{ error() }}</p> }

    @if (datos(); as d) {
      <p class="ayuda">Periodo {{ d.periodo }} · actualizado {{ d.generadoEn | date: 'medium' }} · se refresca cada {{ d.refrescoSegundos }} s</p>
      <div class="kpis">
        <div class="kpi"><app-icono class="kpi-icono" nombre="receipt" [tamano]="20" /><span>Facturado ({{ d.global.facturas | number }} facturas)</span>
          <strong>{{ d.global.facturado | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong></div>
        <div class="kpi"><app-icono class="kpi-icono" nombre="banknote" [tamano]="20" /><span>Recaudado del periodo</span>
          <strong>{{ d.global.recaudado | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong>
          <small>{{ d.global.porcentajeRecaudo | number: '1.0-1' }} %</small></div>
        <div class="kpi"><app-icono class="kpi-icono" nombre="landmark" [tamano]="20" /><span>Recaudo del mes en curso</span>
          <strong>{{ d.global.recaudoMes | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong></div>
        <div class="kpi"><app-icono class="kpi-icono" nombre="wallet" [tamano]="20" /><span>Cartera vencida</span>
          <strong>{{ d.global.carteraVencida | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong>
          <small>{{ d.global.usuariosEnMora | number }} usuarios · {{ d.global.acuerdosVigentes }} acuerdos vigentes</small></div>
        <div class="kpi" [class.alerta]="d.global.pqrVencidas > 0"><app-icono class="kpi-icono" nombre="message-square-text" [tamano]="20" /><span>PQR abiertas / vencidas</span>
          <strong>{{ d.global.pqrAbiertas }} / {{ d.global.pqrVencidas }}</strong></div>
        <div class="kpi" [class.alerta]="d.global.silenciosSinReconocer > 0"><app-icono class="kpi-icono" nombre="triangle-alert" [tamano]="20" /><span>Silencios sin reconocer</span>
          <strong>{{ d.global.silenciosSinReconocer }}</strong></div>
        <div class="kpi"><app-icono class="kpi-icono" nombre="clock" [tamano]="20" /><span>Respuesta media (12 meses)</span>
          <strong>{{ d.global.tiempoMedioRespuestaDias === null ? '—' : (d.global.tiempoMedioRespuestaDias | number: '1.0-1') + ' días' }}</strong>
          <small>{{ d.global.pqrRespondidas12m }} respondidas · {{ d.global.respuestasExtemporaneas12m }} extemporáneas</small></div>
        <div class="kpi"><app-icono class="kpi-icono" nombre="file-text" [tamano]="20" /><span>Contratos activos / suspendidos</span>
          <strong>{{ d.global.contratosActivos | number }} / {{ d.global.contratosSuspendidos | number }}</strong>
          <small>{{ d.global.lecturasEnRevision }} lecturas en revisión</small></div>
      </div>

      <div class="columnas">
        <section class="tarjeta">
          <h2>Cartera por antigüedad</h2>
          @for (e of edades; track e.clave) {
            <div class="barra-dato">
              <span>{{ e.etiqueta }}</span>
              <div class="barra-fondo"><div class="barra-valor" [style.width.%]="porcentajeEdad(d.global.carteraPorEdad[e.clave])"></div></div>
              <span class="num">{{ d.global.carteraPorEdad[e.clave] | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</span>
            </div>
          }
        </section>
        <section class="tarjeta">
          <h2>PQR abiertas por tipo</h2>
          <table class="tabla">
            @for (t of d.global.pqrAbiertasPorTipo | keyvalue; track t.key) {
              <tr><td>{{ t.key }}</td><td class="num">{{ t.value }}</td></tr>
            } @empty {
              <tr><td>No hay PQR abiertas.</td></tr>
            }
          </table>
        </section>
      </div>

      @if (d.porMunicipio.length > 1) {
        <section class="tarjeta">
          <h2>Por municipio</h2>
          <div class="desplazable">
            <table class="tabla">
              <thead>
                <tr><th>Municipio</th><th>Facturado</th><th>Recaudo</th><th>Cartera vencida</th><th>En mora</th>
                  <th>PQR abiertas</th><th>PQR vencidas</th><th>Silencios</th><th>Contratos activos</th></tr>
              </thead>
              <tbody>
                @for (m of d.porMunicipio; track m.municipioId) {
                  <tr>
                    <td>{{ m.municipio }}</td>
                    <td class="num">{{ m.indicadores.facturado | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                    <td class="num">{{ m.indicadores.porcentajeRecaudo | number: '1.0-1' }} %</td>
                    <td class="num">{{ m.indicadores.carteraVencida | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
                    <td class="num">{{ m.indicadores.usuariosEnMora }}</td>
                    <td class="num">{{ m.indicadores.pqrAbiertas }}</td>
                    <td class="num">{{ m.indicadores.pqrVencidas }}</td>
                    <td class="num">{{ m.indicadores.silenciosSinReconocer }}</td>
                    <td class="num">{{ m.indicadores.contratosActivos }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }

      <section class="tarjeta">
        <h2>Tendencia de 12 periodos</h2>
        @for (t of d.tendencia; track t.periodo) {
          <div class="barra-dato">
            <span>{{ t.periodo }}</span>
            <div class="barra-fondo"><div class="barra-valor" [style.width.%]="porcentajeTendencia(t.facturado)"></div></div>
            <span class="num">{{ t.facturado | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} /
              {{ t.recaudado | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</span>
          </div>
        } @empty {
          <p>Sin datos de facturación.</p>
        }
        <p class="ayuda">Facturado / recaudado por periodo.</p>
      </section>
    } @else if (!error()) {
      <p class="cargando">Cargando indicadores…</p>
    }
  `,
})
export class Dashboard implements OnDestroy {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly edades = EDADES;
  readonly datos = signal<Tablero | null>(null);
  readonly error = signal<string | null>(null);

  private readonly fb = inject(NonNullableFormBuilder);
  readonly filtros = this.fb.group({
    periodo: [''],
    municipioId: this.fb.control<number | null>(null),
  });

  private readonly maxEdad = computed(() => {
    const e = this.datos()?.global.carteraPorEdad;
    return e ? Math.max(e.hasta30, e.de31a60, e.de61a90, e.mas90, 1) : 1;
  });
  private readonly maxTendencia = computed(() => Math.max(...(this.datos()?.tendencia ?? []).map((t) => t.facturado), 1));

  private temporizador: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.cargar();
  }

  ngOnDestroy() {
    this.detener();
  }

  porcentajeEdad(valor: number) {
    return (valor / this.maxEdad()) * 100;
  }

  porcentajeTendencia(valor: number) {
    return (valor / this.maxTendencia()) * 100;
  }

  cargar() {
    this.detener();
    const v = this.filtros.getRawValue();
    const periodo = v.periodo.trim();
    this.http
      .get<Tablero>(`${API}/dashboard`, { params: params({ periodo: periodo || null, municipioId: v.municipioId }) })
      .subscribe({
        next: (d) => {
          this.error.set(null);
          this.datos.set(d);
          this.programar(d.refrescoSegundos);
        },
        error: () => {
          this.error.set('No fue posible cargar los indicadores.');
          this.programar(this.datos()?.refrescoSegundos ?? 60);
        },
      });
  }

  private programar(segundos: number) {
    this.temporizador = setTimeout(() => this.cargar(), Math.max(segundos, 10) * 1000);
  }

  private detener() {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
