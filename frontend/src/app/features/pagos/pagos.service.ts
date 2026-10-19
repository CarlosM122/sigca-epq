import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';
import { LineaDetalle } from '../facturacion/facturacion.service';

export type TipoObligacion = 'FACTURA' | 'CUOTA';
export type CanalPago = 'PRESENCIAL' | 'BANCO' | 'EN_LINEA';

/** Lo que se paga con una referencia: el valor exigible es exactamente lo que se cobra (P-05, RN-33). */
export interface Obligacion {
  tipo: TipoObligacion;
  id: number;
  referencia: string;
  usuarioId: number;
  codigoUsuario: string;
  titular: string;
  estado: string;
  totalAPagar: number;
  valorEnReclamo: number;
  valorExigible: number;
  fechaVencimiento?: string | null;
  admitePago: boolean;
  motivoNoAdmite?: string | null;
  lineas?: LineaDetalle[] | null;
}

export interface PagoPresencialDatos {
  referencia: string;
  canal: 'PRESENCIAL' | 'BANCO';
  valor: number;
  referenciaBanco?: string | null;
}

export interface PagoVista {
  id: number;
  codigoPago: string;
  tipoObligacion: TipoObligacion;
  obligacionId: number;
  referencia: string;
  codigoUsuario: string;
  titular: string;
  valor: number;
  canal: CanalPago;
  fechaPago: string;
  codigoTransaccion?: string | null;
  referenciaBanco?: string | null;
  estadoObligacion: string;
  saldoPendiente?: number | null;
  valorEnReclamo?: number | null;
  comprobanteEnviado: boolean;
}

export interface PagoResumen {
  id: number;
  codigoPago: string;
  fechaPago: string;
  valor: number;
  canal: CanalPago;
  tipoObligacion: TipoObligacion;
  referencia: string;
  codigoUsuario: string;
  titular: string;
  codigoTransaccion?: string | null;
  referenciaBanco?: string | null;
  estadoSyncErp: string;
}

export interface InicioPagoEnLinea {
  referencia: string;
  referenciaPago: string;
  monto: number;
  urlCheckout: string;
}

export interface EstadoTransaccion {
  referencia: string;
  referenciaPago: string;
  estado: 'INICIADA' | 'APROBADA' | 'RECHAZADA' | 'ERROR' | 'EXPIRADA';
  monto: number;
  mensaje?: string | null;
  codigoPago?: string | null;
}

/** Referencia de pago: número de factura o de acuerdo, con o sin número de cuota (P-18). */
export const PATRON_REFERENCIA = /^(FAC-\d{6}-\d{7}|AP-\d{4}-\d{6}(-\d{2})?)$/;

/** Solo se sigue una URL https o una ruta absoluta de la propia aplicación (pasarela simulada). */
export function urlCheckoutSegura(url: string): boolean {
  return /^https:\/\//.test(url) || /^\/(?!\/)/.test(url);
}

@Injectable({ providedIn: 'root' })
export class PagosService {
  private readonly http = inject(HttpClient);

  buscarObligacion(referencia: string) {
    return this.http.get<Obligacion>(`${API}/pagos/obligacion`, { params: params({ referencia }) });
  }

  registrar(datos: PagoPresencialDatos) {
    return this.http.post<PagoVista>(`${API}/pagos`, datos);
  }

  pagos(filtro: { codigo?: string; usuario?: string; factura?: string; canal?: string; desde?: string; hasta?: string; page?: number }) {
    return this.http.get<Pagina<PagoResumen>>(`${API}/pagos`, { params: params({ size: 20, ...filtro }) });
  }

  comprobante(id: number) {
    return this.http.get(`${API}/pagos/${id}/comprobante`, { responseType: 'blob' });
  }

  iniciarEnLinea(tipoObligacion: TipoObligacion, obligacionId: number) {
    return this.http.post<InicioPagoEnLinea>(`${API}/portal/pagos`, { tipoObligacion, obligacionId });
  }

  estadoTransaccion(referencia: string) {
    return this.http.get<EstadoTransaccion>(`${API}/publico/pagos/${encodeURIComponent(referencia)}`);
  }
}
