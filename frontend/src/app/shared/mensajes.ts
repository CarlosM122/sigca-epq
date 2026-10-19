import { ValidationErrors } from '@angular/forms';

const NOMBRES_DOCUMENTO: Record<string, string> = {
  CC: 'La cédula debe tener entre 6 y 10 dígitos y no empezar por 0',
  CE: 'La cédula de extranjería debe tener 6 a 10 letras o números',
  NIT: 'El NIT debe tener el formato 900123456-7',
  PA: 'El pasaporte debe tener 5 a 15 letras o números',
  PPT: 'El PPT debe tener entre 6 y 15 dígitos',
  TI: 'La tarjeta de identidad debe tener 10 u 11 dígitos',
};

/** Traduce los errores de un control a un mensaje en español. */
export function mensajeError(errores: ValidationErrors | null): string | null {
  if (!errores) {
    return null;
  }
  const [clave, valor] = Object.entries(errores)[0];
  switch (clave) {
    case 'required':
      return 'Este campo es obligatorio';
    case 'requiredTrue':
      return 'Debe aceptar para continuar';
    case 'minlength':
      return `Mínimo ${valor.requiredLength} caracteres`;
    case 'maxlength':
      return `Máximo ${valor.requiredLength} caracteres`;
    case 'min':
      return `El valor mínimo es ${valor.min}`;
    case 'max':
      return `El valor máximo es ${valor.max}`;
    case 'email':
    case 'correo':
      return 'Ingrese un correo válido';
    case 'pattern':
      return 'El formato no es válido';
    case 'documentoFormato':
      return NOMBRES_DOCUMENTO[valor as string] ?? 'Número de identificación inválido';
    case 'nitDigito':
      return 'El dígito de verificación del NIT no es correcto';
    case 'documentoPersona':
      return 'El NIT es solo para persona jurídica, y una persona jurídica se identifica con NIT';
    case 'passwordSegura':
      return `La contraseña necesita ${(valor as string[]).join(', ')}`;
    case 'noCoinciden':
      return 'Los valores no coinciden';
    case 'sinHtml':
      return 'No se permiten etiquetas HTML ni caracteres de control';
    case 'longitudRecortada':
      return `Debe tener entre ${valor.min} y ${valor.max} caracteres (tiene ${valor.actual})`;
    case 'fechaFutura':
      return 'La fecha no puede ser futura';
    case 'valorMonetario':
      return 'Ingrese un valor mayor que cero con máximo 2 decimales';
    case 'servidor':
      return valor as string;
    default:
      return 'Valor inválido';
  }
}
