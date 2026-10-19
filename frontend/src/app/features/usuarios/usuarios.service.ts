import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';
import { ContratoVista, MedidorVista } from '../contratos/contratos.service';

export interface UsuarioDatos {
  tipoPersona: string;
  tipoIdentificacion: string;
  numeroIdentificacion: string;
  nombreCompleto: string;
  correo: string;
  telefono: string;
  direccionNotificacion: string;
  municipioId: number;
  autorizaTratamientoDatos: boolean;
}

/** La identificación solo viaja enmascarada: el número completo nunca sale del backend. */
export interface UsuarioVista {
  id: number;
  codigo: string;
  tipoPersona: string;
  tipoIdentificacion: string;
  identificacionMascara: string;
  nombreCompleto: string;
  correo: string;
  telefono: string;
  direccionNotificacion: string;
  municipioId: number;
  municipio: string;
  estadoVerificacionRnec: string;
  fechaVerificacionRnec?: string | null;
  activo: boolean;
  fechaInactivacion?: string | null;
  motivoInactivacion?: string | null;
  cuentaPortal: string;
  creadoEn: string;
  version: number;
  /** Aviso no bloqueante (p. ej. Registraduría no disponible). */
  advertencia?: string | null;
}

export interface UsuarioDetalle {
  usuario: UsuarioVista;
  predios: PredioVista[];
  contratos: ContratoVista[];
}

export interface PredioDatos {
  fichaCatastral?: string | null;
  direccion: string;
  barrioVereda?: string | null;
  municipioId: number;
  sectorId?: number | null;
  uso: string;
  estrato?: number | null;
  version?: number | null;
}

export interface PredioVista {
  id: number;
  codigoPredio: string;
  fichaCatastral?: string | null;
  direccion: string;
  barrioVereda?: string | null;
  municipioId: number;
  municipio: string;
  sectorId?: number | null;
  sector?: string | null;
  uso: string;
  estrato?: number | null;
  version: number;
}

export interface PredioDetalle {
  predio: PredioVista;
  medidores: MedidorVista[];
  contratos: ContratoVista[];
}

export interface Sector {
  id: number;
  nombre: string;
  tipo: string;
}

export interface FiltroUsuarios {
  texto?: string;
  tipoIdentificacion?: string;
  numeroIdentificacion?: string;
  municipioId?: number | null;
  activo?: boolean | null;
  page?: number;
}

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);

  buscar(filtro: FiltroUsuarios) {
    return this.http.get<Pagina<UsuarioVista>>(`${API}/usuarios`, { params: params({ size: 20, ...filtro }) });
  }

  detalle(id: number) {
    return this.http.get<UsuarioDetalle>(`${API}/usuarios/${id}`);
  }

  crear(datos: UsuarioDatos) {
    return this.http.post<UsuarioVista>(`${API}/usuarios`, datos);
  }

  /** La identificación no cambia: solo nombre, contacto y municipio. */
  modificar(id: number, datos: Omit<UsuarioDatos, 'tipoPersona' | 'tipoIdentificacion' | 'numeroIdentificacion' | 'autorizaTratamientoDatos'>, version: number) {
    return this.http.put<UsuarioVista>(`${API}/usuarios/${id}`, { ...datos, version });
  }

  actualizarContacto(id: number, datos: { correo: string; telefono: string; direccionNotificacion: string }, version: number) {
    return this.http.put<UsuarioVista>(`${API}/usuarios/${id}/contacto`, { ...datos, version });
  }

  inactivar(id: number, motivo: string, version: number) {
    return this.http.post<UsuarioVista>(`${API}/usuarios/${id}/inactivacion`, { motivo, version });
  }

  reverificar(id: number) {
    return this.http.post<UsuarioVista>(`${API}/usuarios/${id}/verificacion-rnec`, {});
  }

  buscarPredios(filtro: { texto?: string; municipioId?: number | null; page?: number }) {
    return this.http.get<Pagina<PredioVista>>(`${API}/predios`, { params: params({ size: 10, ...filtro }) });
  }

  predio(id: number) {
    return this.http.get<PredioDetalle>(`${API}/predios/${id}`);
  }

  crearPredio(datos: PredioDatos) {
    return this.http.post<PredioVista>(`${API}/predios`, datos);
  }

  modificarPredio(id: number, datos: PredioDatos) {
    return this.http.put<PredioVista>(`${API}/predios/${id}`, datos);
  }

  sectores(municipioId: number) {
    return this.http.get<Sector[]>(`${API}/publico/municipios/${municipioId}/sectores`);
  }
}
