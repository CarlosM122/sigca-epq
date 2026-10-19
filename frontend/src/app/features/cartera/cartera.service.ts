import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';

export type EstadoCobro = 'ACTIVO' | 'SUSPENDIDO' | 'CERRADO';
export type EstadoAcuerdo = 'VIGENTE' | 'CUMPLIDO' | 'INCUMPLIDO' | 'ANULADO';

export interface Moroso {
  usuarioId: number;
  codigoUsuario: string;
  nombre: string;
  municipios: string;
  facturasVencidas: number;
  periodosMora: number;
  saldoVencido: number;
  valorEnReclamo: number;
  deudaExigible: number;
  primerVencimiento: string;
  diasMora: number;
  cobroId?: number | null;
  estadoCobro?: EstadoCobro | null;
  candidatoSuspension: boolean;
  fechaAvisoSuspension?: string | null;
  fechaInicioCobro?: string | null;
  acuerdoVigente: boolean;
  ordenesSuspensionPendientes: number;
  contratosActivos: number;
  puedeSerCandidato: boolean;
}

export interface FiltroMorosos {
  municipioId?: number | null;
  antiguedad?: string;
  cobro?: string;
  texto?: string;
  page?: number;
  sort?: string;
}

export interface OrdenCobro {
  id: number;
  numeroOrden: string;
  contratoId: number;
  numeroContrato: string;
  estado: string;
  fechaOrden: string;
}

export interface Cobro {
  id: number;
  usuarioId: number;
  estado: EstadoCobro;
  fechaInicio: string;
  valorDeuda: number;
  periodosMora: number;
  candidatoSuspension: boolean;
  fechaAvisoSuspension?: string | null;
  acuerdoId?: number | null;
  observacion?: string | null;
  ordenes: OrdenCobro[];
}

export interface FacturaVencida {
  id: number;
  numeroFactura: string;
  periodo: number;
  fechaVencimiento: string;
  totalAPagar: number;
  saldoPendiente: number;
  valorEnReclamo: number;
  uso: string;
  direccion: string;
  municipio: string;
  incluible: boolean;
}

export interface DeudaUsuario {
  usuarioId: number;
  codigoUsuario: string;
  nombre: string;
  facturas: FacturaVencida[];
  totalIncluible: number;
  tasaMaxima: number;
  tasaUsura: number;
  cuotaInicialPct: number;
  minCuotas: number;
  maxCuotas: number;
  acuerdoVigente?: string | null;
}

export interface CuotaPlan {
  numeroCuota: number;
  fechaVencimiento: string;
  valorCapital: number;
  valorInteres: number;
  valorCuota: number;
  saldoDespues: number;
}

export interface SimulacionAcuerdo {
  usuarioId: number;
  facturaIds: number[];
  totalDeuda: number;
  cuotaInicial: number;
  cuotaInicialMinima: number;
  tasaInteresAnual: number;
  tasaMaxima: number;
  tasaInteresMensual: number;
  numeroCuotas: number;
  saldoFinanciado: number;
  cuotaFija: number;
  totalIntereses: number;
  totalAPagar: number;
  cuotas: CuotaPlan[];
}

export interface AcuerdoDatos {
  usuarioId: number;
  facturaIds: number[];
  cuotaInicial: number;
  numeroCuotas: number;
  tasaInteresAnual: number;
}

export interface AcuerdoVista {
  id: number;
  numeroAcuerdo: string;
  usuarioId: number;
  codigoUsuario: string;
  titular: string;
  fechaAcuerdo: string;
  totalDeuda: number;
  cuotaInicial: number;
  tasaInteresAnual: number;
  numeroCuotas: number;
  estado: EstadoAcuerdo;
  fechaEstado: string;
  motivoEstado?: string | null;
  funcionario: string;
  cuotasPagadas: number;
  cuotasVencidas: number;
  proximoVencimiento?: string | null;
  saldoPorPagar: number;
}

export interface AcuerdoDetalle {
  acuerdo: AcuerdoVista;
  facturas: { facturaId: number; numeroFactura: string; periodo: number; valorIncluido: number; estadoFactura: string }[];
  cuotas: { id: number; numeroCuota: number; fechaVencimiento: string; valorCapital: number; valorInteres: number; valorCuota: number; estado: string; fechaPago?: string | null }[];
}

/** Descarga un blob (CSV) con el nombre dado. */
export function guardarArchivo(blob: Blob, nombre: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

@Injectable({ providedIn: 'root' })
export class CarteraService {
  private readonly http = inject(HttpClient);

  morosos(filtro: FiltroMorosos) {
    return this.http.get<Pagina<Moroso>>(`${API}/cartera/morosos`, { params: params({ size: 20, ...filtro }) });
  }

  exportarMorosos(filtro: FiltroMorosos) {
    const { page: _p, sort: _s, ...resto } = filtro;
    return this.http.get(`${API}/cartera/morosos.csv`, { params: params(resto), responseType: 'blob' });
  }

  iniciarCobro(usuarioId: number, observacion: string | null) {
    return this.http.post<Cobro>(`${API}/cartera/cobros`, { usuarioId, observacion });
  }

  marcarCandidato(cobroId: number, observacion: string | null) {
    return this.http.post<Cobro>(`${API}/cartera/cobros/${cobroId}/candidato`, { observacion });
  }

  avisarSuspension(cobroId: number, observacion: string | null) {
    return this.http.post<Cobro>(`${API}/cartera/cobros/${cobroId}/aviso`, { observacion });
  }

  ordenarSuspension(cobroId: number, observacion: string | null) {
    return this.http.post<Cobro>(`${API}/cartera/cobros/${cobroId}/orden-suspension`, { observacion });
  }

  deuda(usuarioId: number) {
    return this.http.get<DeudaUsuario>(`${API}/cartera/usuarios/${usuarioId}/facturas-vencidas`);
  }

  simular(datos: AcuerdoDatos) {
    return this.http.post<SimulacionAcuerdo>(`${API}/cartera/acuerdos/simulacion`, datos);
  }

  crear(datos: AcuerdoDatos) {
    return this.http.post<AcuerdoDetalle>(`${API}/cartera/acuerdos`, datos);
  }

  acuerdos(filtro: { estado?: string; usuarioId?: number | null; texto?: string; page?: number }) {
    return this.http.get<Pagina<AcuerdoVista>>(`${API}/cartera/acuerdos`, { params: params({ size: 20, ...filtro }) });
  }

  acuerdo(id: number) {
    return this.http.get<AcuerdoDetalle>(`${API}/cartera/acuerdos/${id}`);
  }

  anular(id: number, motivo: string) {
    return this.http.post<AcuerdoDetalle>(`${API}/cartera/acuerdos/${id}/anulacion`, { motivo });
  }
}
