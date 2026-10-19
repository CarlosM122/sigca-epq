import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

import { mensajeError } from './mensajes';

/** Muestra el error de un control cuando ya fue tocado o el formulario se intentó enviar. */
@Component({
  selector: 'app-campo-error',
  template: `
    @let c = control();
    @if (c && c.invalid && (c.touched || c.dirty)) {
      <small class="error" role="alert">{{ mensaje() }}</small>
    }
  `,
})
export class CampoError {
  readonly control = input.required<AbstractControl | null>();

  mensaje() {
    return mensajeError(this.control()?.errors ?? null);
  }
}
