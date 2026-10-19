import { FormGroup } from '@angular/forms';

import { ApiError } from '../../core/modelos';

/** Campo del formulario al que corresponde cada error de contraseña del backend (PoliticaPassword y AutenticacionService). */
const CAMPO_POR_CODIGO: Record<string, 'actual' | 'nueva' | 'confirmacion'> = {
  PASSWORD_ACTUAL_INCORRECTA: 'actual',
  PASSWORD_IGUAL_ACTUAL: 'nueva',
  PASSWORD_CORTA: 'nueva',
  PASSWORD_ES_IDENTIFICACION: 'nueva',
  PASSWORD_NO_COINCIDE: 'confirmacion',
};

/**
 * Ubica en su campo los errores de negocio de contraseña. {@code nombres} traduce los campos lógicos a los
 * controles del formulario (en el enlace del portal la nueva contraseña se llama "password").
 */
export function aplicarErrorPassword(
  form: FormGroup,
  error: ApiError,
  nombres: Partial<Record<'actual' | 'nueva' | 'confirmacion', string>> = {},
): boolean {
  const logico = CAMPO_POR_CODIGO[error.codigo];
  const ctrl = logico ? form.get(nombres[logico] ?? logico) : null;
  if (!ctrl) {
    return false;
  }
  ctrl.setErrors({ ...(ctrl.errors ?? {}), servidor: error.mensaje });
  ctrl.markAsTouched();
  return true;
}
