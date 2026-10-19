import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Icono } from './icono';
import { Logo } from './logo';

/** Marco de las pantallas que no requieren sesión: activación, recuperación, PQR pública y resultado del pago. */
@Component({
  selector: 'app-marco-publico',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Logo, Icono],
  template: `
    <header class="cabecera">
      <a routerLink="/ingresar" aria-label="SIGCA, ir a ingresar"><app-logo /></a>
      <nav aria-label="Atención sin ingresar">
        <a routerLink="/pqr-publica/radicar" routerLinkActive="activo"><app-icono nombre="message-square-text" [tamano]="17" />Radicar PQR</a>
        <a routerLink="/pqr-publica/consultar" routerLinkActive="activo"><app-icono nombre="file-search" [tamano]="17" />Consultar PQR</a>
        <a routerLink="/ingresar" class="boton secundario">Ingresar</a>
      </nav>
    </header>
    <main class="cuerpo">
      <ng-content />
    </main>
    <footer class="pie">
      Empresas Públicas del Quindío E.S.P. · Acueducto, alcantarillado y gas · SIGCA
    </footer>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100vh;
      background: radial-gradient(1200px 500px at 10% -10%, #dcebfa 0%, transparent 60%),
                  radial-gradient(900px 400px at 110% 0%, #d9f3f6 0%, transparent 55%), var(--fondo); }
    .cabecera { display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap;
      padding: 1rem 2rem; > a { text-decoration: none; } }
    nav { display: flex; align-items: center; gap: .35rem; flex-wrap: wrap; }
    nav a:not(.boton) { display: inline-flex; align-items: center; gap: .4rem; padding: .45rem .75rem; border-radius: var(--radio-sm);
      color: var(--texto-suave); font-weight: 550; font-size: .9rem; text-decoration: none; }
    nav a:not(.boton):hover, nav a.activo { color: var(--primario); background: rgba(255, 255, 255, .7); }
    nav .boton { margin: 0 0 0 .4rem; min-height: 36px; }
    .cuerpo { flex: 1; width: 100%; max-width: 980px; margin: 0 auto; padding: 1rem 1.25rem 3rem; }
    .pie { text-align: center; font-size: .8rem; color: var(--texto-tenue); padding: 1.5rem 1rem 2rem; }
    @media (max-width: 640px) { .cabecera { padding: .85rem 1rem; } nav a:not(.boton) { padding: .4rem .5rem; } }
  `,
})
export class MarcoPublico {}
