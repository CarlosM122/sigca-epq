import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { Icono } from '../shared/icono';
import { MENU } from './menu';

@Component({
  selector: 'app-inicio',
  imports: [RouterLink, Icono],
  template: `
    <section class="bienvenida">
      <div>
        <p class="bienvenida-saludo">{{ saludo }}</p>
        <h1>Hola, {{ auth.sesion()?.nombre }}</h1>
        @if (auth.esCiudadano()) {
          <p>Desde aquí puede consultar y pagar sus facturas, ver sus contratos y hacer seguimiento a sus PQR.</p>
          <div class="acciones">
            <a class="boton claro" routerLink="/portal/facturas"><app-icono nombre="receipt" [tamano]="18" />Ver mis facturas</a>
            <a class="boton translucido" routerLink="/pqr-publica/radicar"><app-icono nombre="message-square-text" [tamano]="18" />Radicar una PQR</a>
          </div>
        } @else {
          <p>Estos son los módulos habilitados para su rol. También los encuentra en el menú lateral.</p>
        }
      </div>
      <svg class="bienvenida-ondas" viewBox="0 0 400 200" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 120 C 80 90, 160 150, 240 120 S 360 90, 400 110 L400 200 L0 200 Z" fill="rgba(255,255,255,.08)" />
        <path d="M0 150 C 90 120, 170 180, 260 150 S 360 130, 400 145 L400 200 L0 200 Z" fill="rgba(255,255,255,.1)" />
      </svg>
    </section>

    @if (accesos().length) {
      <h2>Accesos rápidos</h2>
      <div class="accesos">
        @for (a of accesos(); track a.ruta) {
          <a class="acceso" [routerLink]="a.ruta">
            <span class="acceso-icono"><app-icono [nombre]="a.icono" [tamano]="22" /></span>
            <span class="acceso-texto">
              <strong>{{ a.texto }}</strong>
              <small>{{ a.descripcion }}</small>
            </span>
            <app-icono class="acceso-flecha" nombre="arrow-right" [tamano]="18" />
          </a>
        }
      </div>
    } @else {
      <p class="aviso-inline">Su usuario aún no tiene módulos asignados. Solicite al administrador los permisos de su rol.</p>
    }

    @if (auth.esCiudadano()) {
      <section class="tarjeta ayuda-portal">
        <app-icono nombre="info" [tamano]="20" />
        <p>¿Tiene una duda sobre su factura? Puede <a routerLink="/portal/pqr">radicar un reclamo</a> sobre una factura específica:
          el valor en reclamo queda en suspenso mientras se resuelve. Las respuestas se dan en 15 días hábiles.</p>
      </section>
    }
  `,
})
export class Inicio {
  readonly auth = inject(AuthService);

  readonly saludo = (() => {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  })();

  readonly accesos = computed(() => {
    const ciudadano = this.auth.esCiudadano();
    return MENU.filter((i) => (i.ciudadano ? ciudadano : !ciudadano && this.auth.tienePermiso(...(i.permisos ?? []))));
  });
}
