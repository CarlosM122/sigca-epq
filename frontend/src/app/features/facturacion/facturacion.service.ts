import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';

export interface LecturaDatos {
  numeroContrato: string;
  periodo: number;
  fechaLectura: string;
  lecturaActual: number;
  observacion?: string | null;
}

export interface LecturaVista {
  id: number;
  numeroContrato: string;
  serialMedidor: string;
  periodo: number;
  fechaLectura: string;
  lecturaAnterior: number;
  lecturaActual: number;
  consumo: number;
  promedioSeisPeriodos?: number | null;
  desviacionPct?: number | null;
  estado: 'REGISTRADA' | 'EN_REVISION' | 'CONFIRMADA' | 'ESTIMADA';
  observacion?: string | null;
}

export interface ResultadoCarga {
  procesadas: number;
  registradas: number;
  enRevision: number;
  errores: { linea: number; mensaje: string }[];
}

export type EstadoFactura = 'PENDIENTE' | 'VENCIDA' | 'EN_ACUERDO' | 'PAGADA' | 'ANULADA';

/** Línea de factura por servicio con su lectura y liquidación (RF-07, P-08). */
export interface LineaDetalle {
  id: number;
  contratoId: number;
  numeroContrato: string;
  tipoServicio: string;
  periodoLectura?: number | null;
  lecturaAnterior?: number | null;
  lecturaActual?: number | null;
  consumo: number;
  consumoBasico: number;
  consumoComplementario: number;
  consumoSuntuario: number;
  consumoEstimado?: boolean | null;
  cargoFijo: number;
  valorConsumo: number;
  subsidio: number;
  contribucion: number;
  otrosCargos: number;
  detalleOtrosCargos?: string | null;
  interesesMora: number;
  totalLinea: number;
  valorAjuste: number;
  estado: string;
  enReclamo?: boolean | null;
}

/** Fila de la búsqueda de facturas. */
export interface FacturaResumen {
  id: number;
  numeroFactura: string;
  periodo: number;
  codigoUsuario: string;
  titular: string;
  codigoPredio: string;
  direccion: string;
  municipio: string;
  fechaEmision: string;
  fechaVencimiento: string;
  totalAPagar: number;
  saldoPendiente: number;
  valorEnReclamo: number;
  valorExigible: number;
  estado: EstadoFactura;
  reexpedicionDeId?: number | null;
}

/** Factura completa: valor exigible = saldo − valor en reclamo (RN-33). */
export interface FacturaDetalle {
  id: number;
  numeroFactura: string;
  referenciaPago: string;
  periodo: number;
  usuarioId: number;
  codigoUsuario: string;
  titular: string;
  predioId: number;
  codigoPredio: string;
  direccion: string;
  municipio: string;
  uso: string;
  estrato?: number | null;
  fechaEmision: string;
  fechaVencimiento: string;
  numeroLineas: number;
  totalAPagar: number;
  valorAjustes: number;
  valorPagado: number;
  valorEnReclamo: number;
  saldoPendiente: number;
  valorExigible: number;
  estado: EstadoFactura;
  motivoAnulacion?: string | null;
  reexpedicionDe?: string | null;
  reexpedidaComo?: string | null;
  loteId?: number | null;
  estadoSyncErp: string;
  consumoEstimado: boolean;
  lineas: LineaDetalle[];
  version: number;
}

/** Compatibilidad con código que aún usa el nombre anterior. */
export type FacturaVista = FacturaDetalle;

export interface ResultadoAnulacion {
  anulada: FacturaDetalle;
  reexpedida?: FacturaDetalle | null;
}

export interface ErrorPredio {
  usuarioId?: number | null;
  predioId?: number | null;
  codigoPredio?: string | null;
  mensaje: string;
}

export interface LoteVista {
  id: number;
  periodo: number;
  estado: 'EN_PROCESO' | 'COMPLETADO' | 'FALLIDO';
  iniciadoEn: string;
  finalizadoEn?: string | null;
  totalPredios: number;
  totalFacturas: number;
  totalErrores: number;
  valorTotal: number;
  ejecutadoPor: string;
  errores: ErrorPredio[];
}

export interface FiltroFacturas {
  numero?: string;
  usuario?: string;
  predio?: string;
  periodo?: number | null;
  estado?: string;
  page?: number;
}

@Injectable({ providedIn: 'root' })
export class FacturacionService {
  private readonly http = inject(HttpClient);

  registrarLectura(datos: LecturaDatos) {
    return this.http.post<LecturaVista>(`${API}/lecturas`, datos);
  }

  cargarLecturas(periodo: number, archivo: File) {
    const fd = new FormData();
    fd.append('periodo', String(periodo));
    fd.append('archivo', archivo, archivo.name);
    return this.http.post<ResultadoCarga>(`${API}/lecturas/carga`, fd);
  }

  lecturas(filtro: { periodo?: number | null; estado?: string; page?: number }) {
    return this.http.get<Pagina<LecturaVista>>(`${API}/lecturas`, { params: params({ size: 20, ...filtro }) });
  }

  confirmarLectura(id: number, confirmada: boolean, observacion: string) {
    return this.http.post<LecturaVista>(`${API}/lecturas/${id}/revision`, { confirmada, observacion });
  }

  facturas(filtro: FiltroFacturas) {
    return this.http.get<Pagina<FacturaResumen>>(`${API}/facturas`, { params: params({ size: 20, ...filtro }) });
  }

  factura(id: number) {
    return this.http.get<FacturaDetalle>(`${API}/facturas/${id}`);
  }

  anular(id: number, motivo: string, reexpedir: boolean, version: number) {
    return this.http.post<ResultadoAnulacion>(`${API}/facturas/${id}/anulacion`, { motivo, reexpedir, version });
  }

  reexpedir(id: number) {
    return this.http.post<FacturaDetalle>(`${API}/facturas/${id}/reexpedicion`, {});
  }

  /** Sin periodo, el backend factura el último periodo cerrado. */
  generarLote(periodo: number | null) {
    return this.http.post<LoteVista>(`${API}/facturas/lotes`, periodo ? { periodo } : {});
  }

  lotes(cantidad = 12) {
    return this.http.get<LoteVista[]>(`${API}/facturas/lotes`, { params: params({ cantidad }) });
  }

  lote(id: number) {
    return this.http.get<LoteVista>(`${API}/facturas/lotes/${id}`);
  }

  pdf(id: number) {
    return this.http.get(`${API}/facturas/${id}/pdf`, { responseType: 'blob' });
  }
}

/** Periodo AAAAMM del mes en curso. */
export function periodoActual(): number {
  const d = new Date();
  return d.getFullYear() * 100 + d.getMonth() + 1;
}

/** Último periodo cerrado (el que factura el lote mensual). */
export function periodoAnterior(): number {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return d.getFullYear() * 100 + d.getMonth() + 1;
}

export function descargarBlob(blob: Blob, nombre: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.click();
  URL.revokeObjectURL(url);
}
