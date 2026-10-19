import { FormGroup } from '@angular/forms';

import { ApiError } from '../core/modelos';

/**
 * Lleva los errores de campo del backend (400 VALIDACION) a los controles del formulario, para que el
 * mensaje del servidor aparezca junto al campo. Devuelve true si alguno se pudo ubicar.
 */
export function aplicarErroresServidor(form: FormGroup, error: ApiError): boolean {
  let ubicado = false;
  for (const e of error.errores ?? []) {
    const ctrl = form.get(e.campo);
    if (ctrl) {
      ctrl.setErrors({ ...(ctrl.errors ?? {}), servidor: e.mensaje });
      ctrl.markAsTouched();
      ubicado = true;
    }
  }
  return ubicado;
}

/** Marca todo como tocado para mostrar los errores y dice si el formulario puede enviarse. */
export function listoParaEnviar(form: FormGroup): boolean {
  form.markAllAsTouched();
  return form.valid;
}

export function hoyIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
