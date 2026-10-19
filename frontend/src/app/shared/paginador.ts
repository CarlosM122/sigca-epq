import { Component, input, output } from '@angular/core';

import { Pagina } from '../core/modelos';

@Component({
  selector: 'app-paginador',
  template: `
    @let p = pagina()?.page;
    @if (p && p.totalPages > 1) {
      <nav class="paginador" aria-label="Paginación">
        <button type="button" class="secundario" [disabled]="p.number === 0" (click)="cambiar.emit(p.number - 1)">Anterior</button>
        <span>Página {{ p.number + 1 }} de {{ p.totalPages }} · {{ p.totalElements }} registros</span>
        <button type="button" class="secundario" [disabled]="p.number + 1 >= p.totalPages" (click)="cambiar.emit(p.number + 1)">Siguiente</button>
      </nav>
    }
  `,
})
export class Paginador {
  readonly pagina = input<Pagina<unknown> | null>(null);
  readonly cambiar = output<number>();
}
