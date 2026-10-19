import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';

export interface ContratoDatos {
  usuarioId: number;
  predioId: number;
  tipoServicio: string;
  /** Opcional: si falta, el backend toma el medidor activo del predio para ese servicio. */
  medidorId?: number | null;
  fechaVinculacion: string;
}

export interface ContratoVista {
  id: number;
  numeroContrato: string;
  usuarioId: number;
  codigoUsuario: string;
  titular: string;
  predioId: number;
  codigoPredio: string;
  direccion: string;
  municipioId: number;
  municipio: string;
  tipoServicio: string;
  medidorId?: number | null;
  serialMedidor?: string | null;
  fechaVinculacion: string;
  estado: string;
  fechaEstado?: string | null;
  motivoEstado?: string | null;
  version: number;
}

export interface MedidorDatos {
  serial: string;
  tipoServicio: string;
  marca?: string | null;
  fechaInstalacion: string;
  lecturaInicial: number;
}

export interface MedidorVista {
  id: number;
  serial: string;
  predioId: number;
  tipoServicio: string;
  marca?: string | null;
  fechaInstalacion: string;
  lecturaInicial: number;
  lecturaActual: number;
  ultimoPeriodo?: number | null;
  lecturaEnRevision: boolean;
  estado: string;
  fechaRetiro?: string | null;
  numeroContrato?: string | null;
  version: number;
}

export interface OrdenVista {
  id: number;
  numeroOrden: string;
  contratoId: number;
  numeroContrato: string;
  tipoServicio: string;
  titular: string;
  direccion: string;
  municipioId: number;
  municipio: string;
  tipo: 'SUSPENSION' | 'RECONEXION';
  causa: string;
  fechaOrden: string;
  fechaLimite?: string | null;
  valorCargo?: number | null;
  estado: string;
  fechaEjecucion?: string | null;
  ejecutadaPor?: string | null;
  motivoAnulacion?: string | null;
  observacion?: string | null;
  creadoPor?: string | null;
  vencida: boolean;
  version: number;
}

export interface FiltroContratos {
  numero?: string;
  usuarioId?: number;
  predioId?: number;
  municipioId?: number | null;
  tipoServicio?: string;
  estado?: string;
  page?: number;
}

export interface FiltroOrdenes {
  estado?: string;
  tipo?: string;
  municipioId?: number | null;
  contratoId?: number;
  vencidas?: boolean;
  page?: number;
  size?: number;
}

/** Contratos (RF-04), medidores y órdenes de suspensión y reconexión (P-11). */
@Injectable({ providedIn: 'root' })
export class ContratosService {
  private readonly http = inject(HttpClient);

  crear(datos: ContratoDatos) {
    return this.http.post<ContratoVista>(`${API}/contratos`, datos);
  }

  buscar(filtro: FiltroContratos) {
    return this.http.get<Pagina<ContratoVista>>(`${API}/contratos`, { params: params({ size: 20, ...filtro }) });
  }

  obtener(id: number) {
    return this.http.get<ContratoVista>(`${API}/contratos/${id}`);
  }

  delUsuario(usuarioId: number) {
    return this.http.get<ContratoVista[]>(`${API}/usuarios/${usuarioId}/contratos`);
  }

  cancelar(id: number, motivo: string, version: number) {
    return this.http.post<ContratoVista>(`${API}/contratos/${id}/cancelacion`, { motivo, version });
  }

  /** Contratos del ciudadano autenticado (portal). */
  misContratos() {
    return this.http.get<ContratoVista[]>(`${API}/portal/contratos`);
  }

  medidores(predioId: number) {
    return this.http.get<MedidorVista[]>(`${API}/predios/${predioId}/medidores`);
  }

  instalarMedidor(predioId: number, datos: MedidorDatos) {
    return this.http.post<MedidorVista>(`${API}/predios/${predioId}/medidores`, datos);
  }

  retirarMedidor(id: number, fechaRetiro: string, version: number) {
    return this.http.post<MedidorVista>(`${API}/medidores/${id}/retiro`, { fechaRetiro, version });
  }

  ordenes(filtro: FiltroOrdenes) {
    return this.http.get<Pagina<OrdenVista>>(`${API}/ordenes`, { params: params({ size: 20, ...filtro }) });
  }

  crearOrden(datos: { contratoId: number; tipo: string; causa: string; observacion: string }) {
    return this.http.post<OrdenVista>(`${API}/ordenes`, datos);
  }

  ejecutarOrden(id: number, observacion: string | null, version: number) {
    return this.http.post<OrdenVista>(`${API}/ordenes/${id}/ejecucion`, { observacion, version });
  }

  anularOrden(id: number, motivo: string, version: number) {
    return this.http.post<OrdenVista>(`${API}/ordenes/${id}/anulacion`, { motivo, version });
  }
}

export const ESTADOS_CONTRATO = ['ACTIVO', 'SUSPENDIDO', 'CANCELADO'];

export const CAUSAS_ORDEN = [
  { valor: 'SOLICITUD_USUARIO', etiqueta: 'Solicitud del usuario' },
  { valor: 'TECNICA', etiqueta: 'Técnica' },
];
