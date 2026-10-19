import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Opcion, Pagina } from '../../core/modelos';

export interface LecturaRegistrar {
  serial: string;
  periodo: number;
  fechaLectura: string;
  lecturaActual?: number | null;
  causaNoLectura?: string | null;
  observacion?: string | null;
}

export interface LecturaVista {
  id: number;
  medidorId: number;
  serial: string;
  tipoServicio: string;
  numeroContrato?: string | null;
  titular?: string | null;
  predioId: number;
  codigoPredio: string;
  direccion: string;
  municipioId: number;
  municipio: string;
  periodo: number;
  fechaLectura: string;
  lecturaAnterior: number;
  lecturaActual?: number | null;
  consumo?: number | null;
  estado: 'REGISTRADA' | 'EN_REVISION' | 'CONFIRMADA' | 'ESTIMADA';
  origen: 'MANUAL' | 'ARCHIVO' | 'SISTEMA';
  causaNoLectura?: string | null;
  promedioReferencia?: number | null;
  desviacionPct?: number | null;
  observacion?: string | null;
  registradaPor?: number | null;
  revisadaPor?: number | null;
  fechaRevision?: string | null;
  creadoEn: string;
}

export interface FilaResultado {
  linea: number;
  serial?: string | null;
  periodo?: number | null;
  exito: boolean;
  lecturaId?: number | null;
  estado?: string | null;
  consumo?: number | null;
  mensaje?: string | null;
}

export interface ResultadoCarga {
  archivo: string;
  procesadas: number;
  registradas: number;
  confirmadas: number;
  enRevision: number;
  estimadas: number;
  conError: number;
  filas: FilaResultado[];
}

export const CAUSAS_NO_LECTURA: Opcion[] = [
  { valor: 'MEDIDOR_DANADO', etiqueta: 'Medidor dañado' },
  { valor: 'PREDIO_CERRADO', etiqueta: 'Predio cerrado' },
  { valor: 'ACCESO_IMPEDIDO', etiqueta: 'Acceso impedido' },
  { valor: 'MEDIDOR_ILEGIBLE', etiqueta: 'Medidor ilegible' },
  { valor: 'OTRA', etiqueta: 'Otra (explique en la observación)' },
];

/** Límites del cargue (CargaLecturasService). */
export const CARGA_MAX_BYTES = 1024 * 1024;

/** Periodo AAAAMM del mes anterior, que es el que normalmente se lee. */
export function periodoAnterior(): number {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return d.getFullYear() * 100 + d.getMonth() + 1;
}

/** Lecturas (P-07, RN-35): registro, cargue por archivo, crítica y revisión. */
@Injectable({ providedIn: 'root' })
export class LecturasService {
  private readonly http = inject(HttpClient);

  registrar(datos: LecturaRegistrar) {
    return this.http.post<LecturaVista>(`${API}/lecturas`, datos);
  }

  cargar(archivo: File) {
    const datos = new FormData();
    datos.append('archivo', archivo, archivo.name);
    return this.http.post<ResultadoCarga>(`${API}/lecturas/archivo`, datos);
  }

  enRevision(municipioId: number | null, page = 0) {
    return this.http.get<Pagina<LecturaVista>>(`${API}/lecturas/revision`, { params: params({ municipioId, page, size: 20 }) });
  }

  revisar(id: number, decision: 'CONFIRMAR' | 'ESTIMAR', observacion: string | null) {
    return this.http.post<LecturaVista>(`${API}/lecturas/${id}/revision`, { decision, observacion });
  }

  historial(filtro: { medidorId?: number; serial?: string; page?: number }) {
    return this.http.get<Pagina<LecturaVista>>(`${API}/lecturas`, { params: params({ size: 24, ...filtro }) });
  }
}
