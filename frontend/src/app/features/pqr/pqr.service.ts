import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API, params } from '../../core/api';
import { Pagina } from '../../core/modelos';

export type TipoPqr = 'PETICION' | 'QUEJA' | 'RECLAMO' | 'RECURSO';
export type EstadoPqr = 'RECIBIDA' | 'EN_TRAMITE' | 'VENCIDA' | 'RESPONDIDA' | 'TRASLADADA_SSPD';
export type DecisionRecurso = 'CONFIRMA' | 'REVOCA' | 'MODIFICA';

export const ESTADOS_PQR: EstadoPqr[] = ['RECIBIDA', 'EN_TRAMITE', 'VENCIDA', 'RESPONDIDA', 'TRASLADADA_SSPD'];
export const DECISIONES: { valor: DecisionRecurso; etiqueta: string }[] = [
  { valor: 'CONFIRMA', etiqueta: 'Confirma la decisión' },
  { valor: 'REVOCA', etiqueta: 'Revoca la decisión' },
  { valor: 'MODIFICA', etiqueta: 'Modifica la decisión' },
];

/** Parte "datos" del multipart de radicación (PqrSolicitudes.Radicar). */
export interface RadicarPqrDatos {
  tipo: TipoPqr;
  numeroContrato?: string | null;
  radicadoOriginal?: string | null;
  solicitaApelacion?: boolean | null;
  numeroFactura?: string | null;
  tipoIdentificacion?: string | null;
  numeroIdentificacion?: string | null;
  nombreContacto: string;
  correoContacto: string;
  telefonoContacto: string;
  municipioId?: number | null;
  descripcion: string;
  autorizaTratamientoDatos?: boolean | null;
}

export interface PqrRadicada {
  id: number;
  numeroRadicado: string;
  tipo: TipoPqr;
  estado: EstadoPqr;
  fechaRegistro: string;
  fechaLimiteRespuesta: string;
}

export interface ConsultaPqr {
  numeroRadicado: string;
  tipo: TipoPqr;
  estado: EstadoPqr;
  fechaRegistro: string;
  fechaLimiteRespuesta: string;
  fechaRespuesta?: string | null;
  respuesta?: string | null;
  decisionRecurso?: DecisionRecurso | null;
  silencioPositivo: boolean;
}

export interface PqrResumen {
  id: number;
  numeroRadicado: string;
  tipo: TipoPqr;
  canal: string;
  estado: EstadoPqr;
  municipioId: number;
  municipio: string;
  numeroContrato?: string | null;
  fechaRegistro: string;
  fechaLimiteRespuesta: string;
  gestorId: number;
  gestor: string;
  silencioPositivo: boolean;
  alertaEnviada: boolean;
  diasHabilesRestantes?: number | null;
}

export interface AdjuntoPqr {
  id: number;
  tipo: 'SOPORTE' | 'EVIDENCIA';
  nombreArchivo: string;
  tipoMime: string;
  tamanoBytes: number;
  cargadoEn: string;
}

export interface ReferenciaPqr {
  id: number;
  numeroRadicado: string;
  estado: EstadoPqr;
}

export interface LineaReclamada {
  id: number;
  numeroFactura: string;
  periodo: number;
  tipoServicio: string;
  totalLinea: number;
  valorAjuste: number;
  estado: string;
  enReclamo: boolean;
}

export interface PqrDetalle {
  id: number;
  numeroRadicado: string;
  tipo: TipoPqr;
  canal: string;
  estado: EstadoPqr;
  municipioId: number;
  municipio: string;
  numeroContrato?: string | null;
  titular?: string | null;
  solicitanteIdentificacion?: string | null;
  fechaRegistro: string;
  fechaLimiteRespuesta: string;
  gestorId: number;
  gestor: string;
  diasHabilesRestantes?: number | null;
  descripcion: string;
  nombreContacto: string;
  correoContacto: string;
  telefonoContacto: string;
  registradaPor?: string | null;
  respuesta?: string | null;
  fechaRespuesta?: string | null;
  respondidaPor?: string | null;
  respuestaExtemporanea: boolean;
  decisionRecurso?: DecisionRecurso | null;
  solicitaApelacion: boolean;
  pqrOrigen?: ReferenciaPqr | null;
  recurso?: ReferenciaPqr | null;
  linea?: LineaReclamada | null;
  fechaTrasladoSspd?: string | null;
  decisionSspd?: DecisionRecurso | null;
  fechaDecisionSspd?: string | null;
  silencioPositivo: boolean;
  fechaLimiteReconocimiento?: string | null;
  fechaReconocimientoSilencio?: string | null;
  alertaEnviada: boolean;
  historial: { fecha: string; estadoAnterior?: EstadoPqr | null; estadoNuevo?: EstadoPqr | null; gestorAnterior?: string | null; gestorNuevo?: string | null; realizadoPor?: string | null }[];
  actuaciones: { fecha: string; funcionario: string; descripcion: string }[];
  adjuntos: AdjuntoPqr[];
}

export interface PqrPortal {
  id: number;
  numeroRadicado: string;
  tipo: TipoPqr;
  estado: EstadoPqr;
  numeroContrato?: string | null;
  numeroFactura?: string | null;
  fechaRegistro: string;
  fechaLimiteRespuesta: string;
  fechaRespuesta?: string | null;
  respuesta?: string | null;
  decisionRecurso?: DecisionRecurso | null;
  decisionSspd?: DecisionRecurso | null;
  silencioPositivo: boolean;
  radicadoOrigen?: string | null;
  radicadoRecurso?: string | null;
  puedeRecurrir: boolean;
  fechaLimiteRecurso?: string | null;
  adjuntos: AdjuntoPqr[];
}

export interface FacturaReclamable {
  numeroFactura: string;
  periodo: number;
  totalLinea: number;
  enReclamo: boolean;
}

export interface PqrRecurrible {
  numeroRadicado: string;
  tipo: TipoPqr;
  fechaRespuesta: string;
  fechaLimiteRecurso: string;
}

export interface ContratoPqr {
  id: number;
  numeroContrato: string;
  tipoServicio: string;
  estado: string;
  titular: string;
  direccion: string;
  municipio: string;
  facturas: FacturaReclamable[];
  recurribles: PqrRecurrible[];
}

export interface ContextoPortal {
  nombre: string;
  correo: string;
  telefono: string;
  contratos: ContratoPqr[];
}

export interface Gestor {
  id: number;
  nombre: string;
  rol: 'GESTOR_PQR' | 'JEFE_PQR';
  municipio?: string | null;
  pqrAbiertas: number;
}

export interface FiltroBandeja {
  estado?: string;
  tipo?: string;
  municipioId?: number | null;
  radicado?: string;
  gestorId?: number | null;
  vencimiento?: string;
  page?: number;
}

function multipart(datos: RadicarPqrDatos, archivos: File[]): FormData {
  const fd = new FormData();
  fd.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
  archivos.forEach((a) => fd.append('adjuntos', a, a.name));
  return fd;
}

/** Descarga un blob con el nombre dado (el nombre lo saneó el backend). */
export function guardarBlob(blob: Blob, nombre: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

@Injectable({ providedIn: 'root' })
export class PqrService {
  private readonly http = inject(HttpClient);

  // ---------------------------------------------------------------- público (sin sesión)

  radicarPublica(datos: RadicarPqrDatos, adjuntos: File[]) {
    return this.http.post<PqrRadicada>(`${API}/publico/pqr`, multipart(datos, adjuntos));
  }

  consultarPublica(radicado: string, tipoIdentificacion: string, numeroIdentificacion: string) {
    return this.http.post<ConsultaPqr>(`${API}/publico/pqr/consulta`, { radicado, tipoIdentificacion, numeroIdentificacion });
  }

  // ---------------------------------------------------------------- portal del ciudadano

  misPqr() {
    return this.http.get<PqrPortal[]>(`${API}/portal/pqr`);
  }

  contextoPortal() {
    return this.http.get<ContextoPortal>(`${API}/portal/pqr/contexto`);
  }

  radicarPortal(datos: RadicarPqrDatos, adjuntos: File[]) {
    return this.http.post<PqrRadicada>(`${API}/portal/pqr`, multipart(datos, adjuntos));
  }

  descargarAdjuntoPortal(pqrId: number, adjuntoId: number) {
    return this.http.get(`${API}/portal/pqr/${pqrId}/adjuntos/${adjuntoId}`, { responseType: 'blob' });
  }

  // ---------------------------------------------------------------- funcionarios

  radicarPresencial(datos: RadicarPqrDatos, adjuntos: File[]) {
    return this.http.post<PqrRadicada>(`${API}/pqr`, multipart(datos, adjuntos));
  }

  contrato(numero: string) {
    return this.http.get<ContratoPqr>(`${API}/pqr/contratos/${encodeURIComponent(numero)}`);
  }

  bandeja(filtros: FiltroBandeja) {
    return this.http.get<Pagina<PqrResumen>>(`${API}/pqr`, {
      params: params({ size: 20, sort: 'fechaLimiteRespuesta,asc', ...filtros }),
    });
  }

  detalle(id: number) {
    return this.http.get<PqrDetalle>(`${API}/pqr/${id}`);
  }

  tomar(id: number) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/tomar`, {});
  }

  registrarActuacion(id: number, descripcion: string) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/actuaciones`, { descripcion });
  }

  adjuntarEvidencia(id: number, archivos: File[]) {
    const fd = new FormData();
    archivos.forEach((a) => fd.append('archivos', a, a.name));
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/evidencias`, fd);
  }

  ajustarLinea(id: number, valorAjuste: number) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/ajuste-linea`, { valorAjuste });
  }

  responder(id: number, respuesta: string, decisionRecurso: DecisionRecurso | null) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/respuesta`, { respuesta, decisionRecurso });
  }

  trasladarSspd(id: number, respuesta: string) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/traslado-sspd`, { respuesta });
  }

  decisionSspd(id: number, decisionSspd: DecisionRecurso, observacion: string | null) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/decision-sspd`, { decisionSspd, observacion });
  }

  reasignar(id: number, gestorId: number, motivo: string) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/reasignacion`, { gestorId, motivo });
  }

  reconocerSilencio(id: number, observacion: string | null) {
    return this.http.post<PqrDetalle>(`${API}/pqr/${id}/reconocimiento-silencio`, { observacion });
  }

  gestores(municipioId?: number | null) {
    return this.http.get<Gestor[]>(`${API}/pqr/gestores`, { params: params({ municipioId }) });
  }

  descargarAdjunto(pqrId: number, adjuntoId: number) {
    return this.http.get(`${API}/pqr/${pqrId}/adjuntos/${adjuntoId}`, { responseType: 'blob' });
  }
}
