import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from '../core/auth.service';
import { Icono } from '../shared/icono';
import { Logo } from '../shared/logo';
import { ItemMenu, MENU, nombreRol } from './menu';

const INICIO: ItemMenu = { ruta: '/inicio', texto: 'Inicio', icono: 'house', grupo: '', descripcion: '' };

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, Icono, Logo],
  template: `
    <div class="app" [class.menu-abierto]="menuAbierto()">
      <aside class="lateral" id="menu-principal">
        <a routerLink="/inicio" class="lateral-marca" (click)="menuAbierto.set(false)"><app-logo [claro]="true" /></a>
        <nav aria-label="Menú principal">
          @for (g of grupos(); track g.titulo) {
            @if (g.titulo) { <p class="lateral-grupo">{{ g.titulo }}</p> }
            @for (item of g.items; track item.ruta) {
              <a [routerLink]="item.ruta" [class.activo]="item.ruta === activa()" [attr.aria-current]="item.ruta === activa() ? 'page' : null"
                (click)="menuAbierto.set(false)">
                <app-icono [nombre]="item.icono" [tamano]="18" />{{ item.texto }}
              </a>
            }
          }
        </nav>
        <p class="lateral-pie">SIGCA · Empresas Públicas del Quindío</p>
      </aside>
      <div class="velo" (click)="menuAbierto.set(false)" aria-hidden="true"></div>

      <div class="principal">
        <header class="superior">
          <button type="button" class="hamburguesa" (click)="menuAbierto.set(!menuAbierto())"
            [attr.aria-expanded]="menuAbierto()" aria-controls="menu-principal" aria-label="Abrir o cerrar el menú">
            <app-icono nombre="menu" [tamano]="22" />
          </button>
          <a routerLink="/inicio" class="superior-logo" aria-label="Inicio"><app-logo [conTexto]="false" [tamano]="32" /></a>
          <span class="superior-fecha">{{ hoy }}</span>
          <div class="cuenta">
            <span class="avatar" aria-hidden="true">{{ iniciales() }}</span>
            <span class="quien">
              <strong>{{ auth.sesion()?.nombre }}</strong>
              <small>{{ perfil() }}</small>
            </span>
            <a routerLink="/cambiar-contrasena" class="boton secundario" title="Cambiar contraseña" aria-label="Cambiar contraseña">
              <app-icono nombre="key-round" [tamano]="16" /><span class="texto-boton">Contraseña</span>
            </a>
            <button type="button" class="secundario" (click)="auth.cerrarSesion()" title="Cerrar sesión" aria-label="Salir">
              <app-icono nombre="log-out" [tamano]="16" /><span class="texto-boton">Salir</span>
            </button>
          </div>
        </header>
        <main class="contenido">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class Shell {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly menuAbierto = signal(false);
  readonly hoy = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());

  readonly menu = computed(() => {
    const ciudadano = this.auth.esCiudadano();
    return MENU.filter((i) => (i.ciudadano ? ciudadano : !ciudadano && this.auth.tienePermiso(...(i.permisos ?? []))));
  });

  readonly grupos = computed(() => {
    const grupos: { titulo: string; items: ItemMenu[] }[] = [{ titulo: '', items: [INICIO] }];
    for (const item of this.menu()) {
      const g = grupos.find((x) => x.titulo === item.grupo);
      if (g) g.items.push(item);
      else grupos.push({ titulo: item.grupo, items: [item] });
    }
    return grupos;
  });

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map((e) => (e as NavigationEnd).urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Opción activa: la de ruta más larga que contiene la URL (así Órdenes no enciende también Contratos). */
  readonly activa = computed(() => {
    const url = this.url().split(/[?#]/)[0];
    return [INICIO, ...this.menu()]
      .filter((i) => url === i.ruta || url.startsWith(i.ruta + '/'))
      .sort((a, b) => b.ruta.length - a.ruta.length)[0]?.ruta;
  });

  readonly iniciales = computed(() =>
    (this.auth.sesion()?.nombre ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join(''),
  );

  readonly perfil = computed(() => {
    const s = this.auth.sesion();
    if (!s) return '';
    return s.tipo === 'CIUDADANO' ? 'Usuario del portal' : s.roles.map(nombreRol).join(' · ') || 'Funcionario';
  });
}
