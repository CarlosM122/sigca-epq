import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';

export interface FuncionarioVista {
  id: number;
  codigoFuncionario: string;
  nombreCompleto: string;
  correo: string;
  telefono?: string | null;
  username: string;
  municipioId?: number | null;
  municipio?: string | null;
  roles: string[];
  activo: boolean;
  dobleFactorActivo: boolean;
  cambioPasswordRequerido: boolean;
  bloqueadoHasta?: string | null;
  ultimoAcceso?: string | null;
  fechaDesactivacion?: string | null;
  version: number;
}

export interface FuncionarioNuevo {
  codigoFuncionario: string;
  nombreCompleto: string;
  correo: string;
  telefono?: string | null;
  username: string;
  municipioId?: number | null;
  roles: string[];
}

export interface FuncionarioCambios {
  nombreCompleto: string;
  correo: string;
  telefono?: string | null;
  municipioId?: number | null;
  version: number;
}

/** La contraseña temporal solo llega en esta respuesta. */
export interface FuncionarioCreado {
  funcionario: FuncionarioVista;
  passwordTemporal: string;
}

export interface RolVista {
  id: number;
  codigo: string;
  nombre: string;
  descripcion?: string | null;
  activo: boolean;
  permisos: string[];
}

export interface PermisoVista {
  id: number;
  codigo: string;
  modulo: string;
  descripcion: string;
}

export type TipoDato = 'ENTERO' | 'DECIMAL' | 'TEXTO' | 'BOOLEANO';

export interface ParametroVista {
  clave: string;
  valor: string;
  tipoDato: TipoDato;
  descripcion: string;
  actualizadoEn: string;
  actualizadoPor: string;
}

export interface FestivoVista {
  fecha: string;
  descripcion: string;
}

export interface MunicipioAdmin {
  id: number;
  codigoDane: string;
  nombre: string;
  activo: boolean;
}

export type TipoSector = 'ZONA_RURAL' | 'CORREGIMIENTO' | 'VEREDA' | 'URBANO';

export interface SectorVista {
  id: number;
  municipioId: number;
  nombre: string;
  tipo: TipoSector;
  activo: boolean;
}

export interface RegistroAuditoria {
  id: number;
  fecha: string;
  actorTipo: 'FUNCIONARIO' | 'CIUDADANO' | 'SISTEMA' | 'ANONIMO';
  actor: string;
  funcionarioId?: number | null;
  usuarioId?: number | null;
  operacion: string;
  entidad?: string | null;
  entidadId?: number | null;
  resultado: 'EXITO' | 'FALLO';
  detalle?: string | null;
  ipOrigen?: string | null;
}

export interface FiltroAuditoria {
  operacion?: string;
  usuario?: string;
  entidad?: string;
  entidadId?: number | null;
  resultado?: string;
  desde?: string;
  hasta?: string;
  page?: number;
}

/** API de administración (RF-14, RF-15, RF-29). Cada endpoint exige su permiso en el backend. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API}/admin`;

  // Funcionarios y roles (FUNCIONARIO_GESTIONAR, ROL_GESTIONAR)

  funcionarios(filtro: { texto?: string; activo?: boolean | null; page?: number }) {
    return this.http.get<Pagina<FuncionarioVista>>(`${this.base}/funcionarios`, { params: params({ size: 20, ...filtro }) });
  }

  crear(datos: FuncionarioNuevo) {
    return this.http.post<FuncionarioCreado>(`${this.base}/funcionarios`, datos);
  }

  actualizar(id: number, datos: FuncionarioCambios) {
    return this.http.put<FuncionarioVista>(`${this.base}/funcionarios/${id}`, datos);
  }

  asignarRoles(id: number, roles: string[]) {
    return this.http.put<FuncionarioVista>(`${this.base}/funcionarios/${id}/roles`, { roles });
  }

  cambiarEstado(id: number, activo: boolean) {
    return this.http.post<FuncionarioVista>(`${this.base}/funcionarios/${id}/estado`, { activo });
  }

  desbloquear(id: number) {
    return this.http.post<FuncionarioVista>(`${this.base}/funcionarios/${id}/desbloqueo`, {});
  }

  reiniciarDobleFactor(id: number) {
    return this.http.post<FuncionarioVista>(`${this.base}/funcionarios/${id}/doble-factor/reinicio`, {});
  }

  restablecerPassword(id: number) {
    return this.http.post<FuncionarioCreado>(`${this.base}/funcionarios/${id}/password-temporal`, {});
  }

  roles() {
    return this.http.get<RolVista[]>(`${this.base}/roles`);
  }

  permisos() {
    return this.http.get<PermisoVista[]>(`${this.base}/permisos`);
  }

  asignarPermisos(rol: string, permisos: string[]) {
    return this.http.put<RolVista>(`${this.base}/roles/${rol}/permisos`, { permisos });
  }

  // Parámetros, festivos, municipios y sectores (PARAMETRO_GESTIONAR)

  parametros() {
    return this.http.get<ParametroVista[]>(`${this.base}/parametros`);
  }

  modificarParametro(clave: string, valor: string) {
    return this.http.put<ParametroVista>(`${this.base}/parametros/${clave}`, { valor });
  }

  festivos(anio: number) {
    return this.http.get<FestivoVista[]>(`${this.base}/festivos`, { params: params({ anio }) });
  }

  agregarFestivo(fecha: string, descripcion: string) {
    return this.http.post<FestivoVista>(`${this.base}/festivos`, { fecha, descripcion });
  }

  eliminarFestivo(fecha: string) {
    return this.http.delete<void>(`${this.base}/festivos/${fecha}`);
  }

  municipios() {
    return this.http.get<MunicipioAdmin[]>(`${this.base}/municipios`);
  }

  estadoMunicipio(id: number, activo: boolean) {
    return this.http.put<MunicipioAdmin>(`${this.base}/municipios/${id}/estado`, { activo });
  }

  sectores(municipioId: number) {
    return this.http.get<SectorVista[]>(`${this.base}/municipios/${municipioId}/sectores`);
  }

  crearSector(municipioId: number, nombre: string, tipo: TipoSector) {
    return this.http.post<SectorVista>(`${this.base}/municipios/${municipioId}/sectores`, { nombre, tipo });
  }

  modificarSector(id: number, nombre: string, tipo: TipoSector) {
    return this.http.put<SectorVista>(`${this.base}/sectores/${id}`, { nombre, tipo });
  }

  estadoSector(id: number, activo: boolean) {
    return this.http.put<SectorVista>(`${this.base}/sectores/${id}/estado`, { activo });
  }

  // Auditoría (AUDITORIA_CONSULTAR)

  auditoria(filtro: FiltroAuditoria) {
    return this.http.get<Pagina<RegistroAuditoria>>(`${this.base}/auditoria`, { params: params({ size: 50, ...filtro }) });
  }

  operacionesAuditables() {
    return this.http.get<string[]>(`${this.base}/auditoria/operaciones`);
  }
}
