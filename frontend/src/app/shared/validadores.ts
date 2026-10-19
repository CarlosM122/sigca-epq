import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * Validadores que replican los del backend (paquete common.validation) y los CHECK de Oracle.
 * Mejoran la experiencia; la validación que manda sigue siendo la del servidor.
 */

export const PATRONES_IDENTIFICACION: Record<string, RegExp> = {
  CC: /^[1-9]\d{5,9}$/,
  CE: /^[A-Z0-9]{6,10}$/,
  NIT: /^\d{9}-\d$/,
  PA: /^[A-Z0-9]{5,15}$/,
  PPT: /^\d{6,15}$/,
  TI: /^\d{10,11}$/,
};

const PESOS_DIAN = [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71];

/** Dígito de verificación del NIT (módulo 11, DIAN). */
export function digitoVerificacionNit(numero: string): number {
  let suma = 0;
  for (let i = 0; i < numero.length; i++) {
    suma += Number(numero[numero.length - 1 - i]) * PESOS_DIAN[i];
  }
  const r = suma % 11;
  return r > 1 ? 11 - r : r;
}

export function identificacionValida(tipo: string, numero: string): boolean {
  const patron = PATRONES_IDENTIFICACION[tipo];
  if (!patron || !patron.test(numero)) {
    return false;
  }
  if (tipo === 'NIT') {
    const [base, dv] = numero.split('-');
    return digitoVerificacionNit(base) === Number(dv);
  }
  return true;
}

/**
 * Validador de grupo: el número debe cumplir el formato de su tipo, el NIT su dígito de verificación,
 * y (si el grupo tiene tipoPersona) NIT ⇔ persona jurídica.
 */
export function documentoValido(campoTipo = 'tipoIdentificacion', campoNumero = 'numeroIdentificacion'): ValidatorFn {
  return (grupo: AbstractControl): ValidationErrors | null => {
    const tipo = grupo.get(campoTipo)?.value as string;
    const numeroCtrl = grupo.get(campoNumero);
    const numero = ((numeroCtrl?.value as string) ?? '').trim().toUpperCase();
    if (!tipo || !numero) {
      return null;
    }
    let error: ValidationErrors | null = null;
    if (!PATRONES_IDENTIFICACION[tipo]?.test(numero)) {
      error = { documentoFormato: tipo };
    } else if (tipo === 'NIT' && !identificacionValida(tipo, numero)) {
      error = { nitDigito: true };
    }
    const persona = grupo.get('tipoPersona')?.value as string | undefined;
    if (!error && persona && (persona === 'JURIDICA') !== (tipo === 'NIT')) {
      error = { documentoPersona: true };
    }
    marcar(numeroCtrl, error, ['documentoFormato', 'nitDigito', 'documentoPersona']);
    return error;
  };
}

/** Celular (3xxxxxxxxx) o fijo nacional (60xxxxxxxx), con +57 opcional. */
export const telefonoColombia: ValidatorFn = Validators.pattern(/^(\+57)?(3\d{9}|60\d{8})$/);

/** Mismo criterio que CK_USUARIO_CORREO: minúsculas, sin espacios. */
export const correo: ValidatorFn = (c) => {
  const v = (c.value as string) ?? '';
  if (!v) {
    return null;
  }
  return /^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$/.test(v.trim().toLowerCase()) && v.length <= 254
    ? null
    : { correo: true };
};

/** Letras (con tildes y ñ), espacios, punto, apóstrofo y guion; 3 a 150 caracteres. */
export const nombrePersona: ValidatorFn = Validators.pattern(/^\p{L}[\p{L} .'-]{2,149}$/u);

/** Razón social: además admite dígitos y , & ( ) /. */
export const razonSocial: ValidatorFn = Validators.pattern(/^[\p{L}\p{N}][\p{L}\p{N} .,&'()/-]{2,149}$/u);

/** 8 a 64 caracteres con mayúscula, minúscula, dígito y símbolo (7.10). */
export const passwordSegura: ValidatorFn = (c) => {
  const v = (c.value as string) ?? '';
  if (!v) {
    return null;
  }
  const faltantes: string[] = [];
  if (v.length < 8 || v.length > 64) faltantes.push('entre 8 y 64 caracteres');
  if (!/[A-Z]/.test(v)) faltantes.push('una mayúscula');
  if (!/[a-z]/.test(v)) faltantes.push('una minúscula');
  if (!/\d/.test(v)) faltantes.push('un número');
  if (!/[^A-Za-z0-9]/.test(v)) faltantes.push('un símbolo');
  return faltantes.length ? { passwordSegura: faltantes } : null;
};

/** Rechaza etiquetas HTML y caracteres de control en textos libres. */
export const sinHtml: ValidatorFn = (c) => {
  const v = (c.value as string) ?? '';
  // eslint-disable-next-line no-control-regex
  return /<[^>]*>|[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(v) ? { sinHtml: true } : null;
};

/** Texto con longitud mínima y máxima contando sin espacios en los extremos (como TRIM en Oracle). */
export function longitudRecortada(min: number, max: number): ValidatorFn {
  return (c) => {
    const v = ((c.value as string) ?? '').trim();
    if (!v) {
      return null;
    }
    return v.length < min || v.length > max ? { longitudRecortada: { min, max, actual: v.length } } : null;
  };
}

/** Validador de grupo: dos campos deben coincidir (confirmación de contraseña). */
export function coinciden(campo: string, confirmacion: string): ValidatorFn {
  return (grupo) => {
    const ctrl = grupo.get(confirmacion);
    const error = grupo.get(campo)?.value !== ctrl?.value ? { noCoinciden: true } : null;
    marcar(ctrl, error, ['noCoinciden']);
    return error;
  };
}

export const fechaNoFutura: ValidatorFn = (c) => {
  if (!c.value) {
    return null;
  }
  const hoy = new Date();
  hoy.setHours(23, 59, 59, 999);
  return new Date(`${c.value}T00:00:00`) > hoy ? { fechaFutura: true } : null;
};

/** Periodo AAAAMM entre 2000 y 2099. */
export const periodo: ValidatorFn = Validators.pattern(/^20\d{2}(0[1-9]|1[0-2])$/);

/** Valores en pesos: positivo y con máximo 2 decimales (NUMBER(14,2)). */
export const valorMonetario: ValidatorFn = (c) => {
  const v = c.value;
  if (v === null || v === undefined || v === '') {
    return null;
  }
  const n = Number(v);
  return Number.isFinite(n) && n > 0 && n < 1e12 && /^\d+(\.\d{1,2})?$/.test(String(v)) ? null : { valorMonetario: true };
};

export const TIPOS_ARCHIVO = ['application/pdf', 'image/jpeg', 'image/png'];

/** Revisa tipo y tamaño de adjuntos antes de subirlos; el backend vuelve a validar el contenido real. */
export function validarArchivos(archivos: File[], maxArchivos = 5, maxMb = 10): string | null {
  if (archivos.length > maxArchivos) {
    return `Máximo ${maxArchivos} archivos`;
  }
  for (const a of archivos) {
    if (!TIPOS_ARCHIVO.includes(a.type)) {
      return `"${a.name}" no es PDF, JPG ni PNG`;
    }
    if (a.size === 0 || a.size > maxMb * 1024 * 1024) {
      return `"${a.name}" debe pesar entre 1 byte y ${maxMb} MB`;
    }
  }
  return null;
}

/** Pone o quita un error en un control hijo sin pisar sus otros errores. */
function marcar(ctrl: AbstractControl | null | undefined, error: ValidationErrors | null, claves: string[]) {
  if (!ctrl) {
    return;
  }
  const actuales = { ...(ctrl.errors ?? {}) };
  claves.forEach((k) => delete actuales[k]);
  const nuevos = { ...actuales, ...(error ?? {}) };
  ctrl.setErrors(Object.keys(nuevos).length ? nuevos : null);
}
