import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';

export interface TarifaDatos {
  tipoServicio: string;
  municipioId: number;
  cargoFijo: number;
  valorM3Basico: number;
  valorM3Complementario?: number | null;
  valorM3Suntuario?: number | null;
  consumoBasicoM3?: number | null;
  consumoSuntuarioDesdeM3?: number | null;
  vigenteDesde: string;
  resolucion: string;
}

export interface TarifaVista extends TarifaDatos {
  id: number;
  municipio: string;
  vigenteHasta?: string | null;
  vigenteHoy: boolean;
  creadoPor: string;
  creadoEn: string;
  version: number;
}

export interface FactorDatos {
  municipioId: number;
  tipoServicio: string;
  uso: string;
  estrato?: number | null;
  porcentaje: number;
  vigenteDesde: string;
  acuerdoMunicipal: string;
}

export interface FactorVista extends FactorDatos {
  id: number;
  municipio: string;
  vigenteHasta?: string | null;
  vigenteHoy: boolean;
  creadoPor: string;
  creadoEn: string;
  version: number;
}

/** Resultado de "nueva vigencia": la anterior cerrada el día previo (si había) y la nueva. */
export interface NuevaVigencia<T> {
  cerrada?: T | null;
  nueva: T;
}

export interface FiltroVigencias {
  municipioId?: number | null;
  tipoServicio?: string;
  uso?: string;
  vigenteEn?: string;
  page?: number;
}

/** Topes legales de CK_FACTOR_TOPES: [mínimo, máximo] del porcentaje (negativo = subsidio). */
export function topesFactor(uso: string, estrato: number | null): [number, number] {
  if (uso === 'RESIDENCIAL') {
    return ({ 1: [-70, 0], 2: [-40, 0], 3: [-15, 0], 4: [0, 0] } as Record<number, [number, number]>)[estrato ?? 0] ?? [0, 100];
  }
  return uso === 'OFICIAL' ? [0, 0] : [0, 100];
}

@Injectable({ providedIn: 'root' })
export class TarifasService {
  private readonly http = inject(HttpClient);

  tarifas(filtro: FiltroVigencias) {
    return this.http.get<Pagina<TarifaVista>>(`${API}/tarifas`, { params: params({ size: 20, ...filtro }) });
  }

  crearTarifa(datos: TarifaDatos) {
    return this.http.post<TarifaVista>(`${API}/tarifas`, datos);
  }

  nuevaVigenciaTarifa(datos: TarifaDatos) {
    return this.http.post<NuevaVigencia<TarifaVista>>(`${API}/tarifas/nueva-vigencia`, datos);
  }

  cerrarTarifa(id: number, vigenteHasta: string, version: number) {
    return this.http.post<TarifaVista>(`${API}/tarifas/${id}/cierre`, { vigenteHasta, version });
  }

  factores(filtro: FiltroVigencias) {
    return this.http.get<Pagina<FactorVista>>(`${API}/factores-subsidio`, { params: params({ size: 20, ...filtro }) });
  }

  crearFactor(datos: FactorDatos) {
    return this.http.post<FactorVista>(`${API}/factores-subsidio`, datos);
  }

  nuevaVigenciaFactor(datos: FactorDatos) {
    return this.http.post<NuevaVigencia<FactorVista>>(`${API}/factores-subsidio/nueva-vigencia`, datos);
  }

  cerrarFactor(id: number, vigenteHasta: string, version: number) {
    return this.http.post<FactorVista>(`${API}/factores-subsidio/${id}/cierre`, { vigenteHasta, version });
  }
}
