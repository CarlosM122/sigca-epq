/** Formato de error único que devuelve el backend (GlobalExceptionHandler). */
export interface ApiError {
  timestamp: string;
  status: number;
  codigo: string;
  mensaje: string;
  ruta: string;
  errores: CampoInvalido[];
}

export interface CampoInvalido {
  campo: string;
  mensaje: string;
  valorRechazado?: unknown;
}

export interface Pagina<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export type TipoActor = 'FUNCIONARIO' | 'CIUDADANO';

/** Datos de la sesión tomados de la respuesta de login (el token es opaco para el cliente). */
export interface Sesion {
  token: string;
  expiraEn: string;
  tipo: TipoActor;
  nombre: string;
  roles: string[];
  permisos: string[];
  municipioId?: number | null;
  cambioPasswordRequerido: boolean;
}

export interface Municipio {
  id: number;
  codigoDane: string;
  nombre: string;
}

export interface Opcion<T = string> {
  valor: T;
  etiqueta: string;
}

export const TIPOS_IDENTIFICACION: Opcion[] = [
  { valor: 'CC', etiqueta: 'Cédula de ciudadanía' },
  { valor: 'CE', etiqueta: 'Cédula de extranjería' },
  { valor: 'TI', etiqueta: 'Tarjeta de identidad' },
  { valor: 'PA', etiqueta: 'Pasaporte' },
  { valor: 'PPT', etiqueta: 'Permiso por Protección Temporal' },
  { valor: 'NIT', etiqueta: 'NIT' },
];

export const TIPOS_PQR: Opcion[] = [
  { valor: 'PETICION', etiqueta: 'Petición' },
  { valor: 'QUEJA', etiqueta: 'Queja' },
  { valor: 'RECLAMO', etiqueta: 'Reclamo' },
  { valor: 'RECURSO', etiqueta: 'Recurso' },
];

export const USOS_PREDIO: Opcion[] = [
  { valor: 'RESIDENCIAL', etiqueta: 'Residencial' },
  { valor: 'COMERCIAL', etiqueta: 'Comercial' },
  { valor: 'INDUSTRIAL', etiqueta: 'Industrial' },
  { valor: 'OFICIAL', etiqueta: 'Oficial' },
];

export const TIPOS_SERVICIO: Opcion[] = [
  { valor: 'ACUEDUCTO', etiqueta: 'Acueducto' },
  { valor: 'ALCANTARILLADO', etiqueta: 'Alcantarillado' },
  { valor: 'GAS', etiqueta: 'Gas' },
];

export function esApiError(e: unknown): e is ApiError {
  return !!e && typeof e === 'object' && 'codigo' in e && 'mensaje' in e;
}
