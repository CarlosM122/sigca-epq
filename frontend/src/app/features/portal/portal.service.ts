import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';
import { FacturaDetalle } from '../facturacion/facturacion.service';
import { InicioPagoEnLinea, PagoResumen, TipoObligacion } from '../pagos/pagos.service';

export interface ContratoPortal {
  id: number;
  numeroContrato: string;
  tipoServicio: string;
  estado: string;
  fechaVinculacion: string;
  predioId: number;
  codigoPredio: string;
  direccion: string;
  municipio: string;
  uso: string;
  estrato?: number | null;
  serialMedidor?: string | null;
}

export interface ConsumoPortal {
  periodo: number;
  fechaLectura: string;
  lecturaAnterior: number;
  lecturaActual?: number | null;
  consumo?: number | null;
  estado: 'REGISTRADA' | 'EN_REVISION' | 'CONFIRMADA' | 'ESTIMADA';
  origen: string;
}

export interface ConsumosContrato {
  contratoId: number;
  numeroContrato: string;
  tipoServicio: string;
  serialMedidor?: string | null;
  nota?: string | null;
  promedio?: number | null;
  consumos: ConsumoPortal[];
}

export interface CuotaPortal {
  id: number;
  numeroCuota: number;
  referencia: string;
  valorCuota: number;
  fechaVencimiento: string;
  estado: string;
  fechaPago?: string | null;
  pagable: boolean;
  motivoNoPagable?: string | null;
}

export interface AcuerdoPortal {
  id: number;
  numeroAcuerdo: string;
  fechaAcuerdo: string;
  totalIncluido: number;
  cuotaInicial: number;
  numeroCuotas: number;
  estado: string;
  cuotas: CuotaPortal[];
}

/** Portal ciudadano: el backend filtra todo por el titular de la sesión. */
@Injectable({ providedIn: 'root' })
export class PortalService {
  private readonly http = inject(HttpClient);

  contratos() {
    return this.http.get<ContratoPortal[]>(`${API}/portal/contratos`);
  }

  consumos(contratoId: number) {
    return this.http.get<ConsumosContrato>(`${API}/portal/consumos`, { params: params({ contratoId }) });
  }

  facturas() {
    return this.http.get<FacturaDetalle[]>(`${API}/portal/facturas`);
  }

  factura(id: number) {
    return this.http.get<FacturaDetalle>(`${API}/portal/facturas/${id}`);
  }

  disponibilidad() {
    return this.http.get<{ fechaEstimada: string }>(`${API}/portal/facturas/disponibilidad`);
  }

  pdf(facturaId: number) {
    return this.http.get(`${API}/portal/facturas/${facturaId}/pdf`, { responseType: 'blob' });
  }

  acuerdos() {
    return this.http.get<AcuerdoPortal[]>(`${API}/portal/acuerdos`);
  }

  pagos(page = 0) {
    return this.http.get<Pagina<PagoResumen>>(`${API}/portal/pagos`, { params: params({ page, size: 10 }) });
  }

  comprobante(pagoId: number) {
    return this.http.get(`${API}/portal/pagos/${pagoId}/comprobante`, { responseType: 'blob' });
  }

  pagar(tipoObligacion: TipoObligacion, obligacionId: number) {
    return this.http.post<InicioPagoEnLinea>(`${API}/portal/pagos`, { tipoObligacion, obligacionId });
  }
}
