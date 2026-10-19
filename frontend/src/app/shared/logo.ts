import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let siguiente = 0;

/** Marca de SIGCA: gota de agua con onda, y opcionalmente el nombre del sistema. */
@Component({
  selector: 'app-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'logo', '[class.claro]': 'claro()' },
  template: `
    <svg viewBox="0 0 40 40" [attr.width]="tamano()" [attr.height]="tamano()" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="idGradiente" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#38bdf8" />
          <stop offset="1" stop-color="#0b5fa5" />
        </linearGradient>
      </defs>
      <rect width="40" height="40" rx="11" [attr.fill]="claro() ? 'rgba(255,255,255,.12)' : '#e8f1fb'" />
      <path d="M20 7.5c4.6 6 8.5 10.6 8.5 15.4a8.5 8.5 0 0 1-17 0C11.5 18.1 15.4 13.5 20 7.5z" [attr.fill]="'url(#' + idGradiente + ')'" />
      <path d="M13.2 24.2c2.3 1.5 4.5 1.5 6.8 0s4.5-1.5 6.8 0" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" opacity=".9" />
    </svg>
    @if (conTexto()) {
      <span class="logo-texto">
        <strong>SIGCA</strong>
        <small>Empresas Públicas del Quindío</small>
      </span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: .7rem; color: var(--texto); text-decoration: none; }
    :host(.claro) { color: #fff; }
    svg { flex: none; }
    .logo-texto { display: flex; flex-direction: column; line-height: 1.15; }
    strong { font-size: 1.05rem; letter-spacing: .04em; }
    small { font-size: .72rem; opacity: .75; font-weight: 500; }
  `,
})
export class Logo {
  /** Id único por instancia: si una copia oculta define el degradado, las demás no lo encuentran. */
  readonly idGradiente = `logo-gota-${++siguiente}`;
  readonly tamano = input(36);
  readonly conTexto = input(true);
  readonly claro = input(false);
}
