import { Component, inject } from '@angular/core';

import { AvisosService } from '../core/avisos.service';

@Component({
  selector: 'app-avisos',
  template: `
    <div class="avisos" aria-live="polite">
      @for (a of avisos.avisos(); track a.id) {
        <div class="aviso" [class]="a.tipo" role="status">
          <span>{{ a.texto }}</span>
          <button type="button" class="cerrar" (click)="avisos.cerrar(a.id)" aria-label="Cerrar">×</button>
        </div>
      }
    </div>
  `,
})
export class Avisos {
  readonly avisos = inject(AvisosService);
}
