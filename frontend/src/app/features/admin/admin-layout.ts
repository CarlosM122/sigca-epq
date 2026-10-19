import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth.service';

/** Secciones de administración, cada una visible solo con su permiso (el backend vuelve a exigirlo). */
export const SECCIONES_ADMIN = [
  { ruta: 'funcionarios', texto: 'Funcionarios', permisos: ['FUNCIONARIO_GESTIONAR'] },
  { ruta: 'roles', texto: 'Roles y permisos', permisos: ['ROL_GESTIONAR'] },
  { ruta: 'parametros', texto: 'Parámetros', permisos: ['PARAMETRO_GESTIONAR'] },
  { ruta: 'festivos', texto: 'Festivos', permisos: ['PARAMETRO_GESTIONAR'] },
  { ruta: 'municipios', texto: 'Municipios y sectores', permisos: ['PARAMETRO_GESTIONAR'] },
  { ruta: 'auditoria', texto: 'Auditoría', permisos: ['AUDITORIA_CONSULTAR'] },
];

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav class="secciones" aria-label="Administración">
      @for (s of secciones(); track s.ruta) {
        <a [routerLink]="s.ruta" routerLinkActive="activa" ariaCurrentWhenActive="page">{{ s.texto }}</a>
      }
    </nav>
    <router-outlet />
  `,
  styles: `
    .secciones { display: flex; flex-wrap: wrap; gap: .25rem; margin: 0 0 1.5rem; border-bottom: 1px solid var(--borde); }
    .secciones a { padding: .65rem .9rem; margin-bottom: -1px; border-bottom: 2px solid transparent; text-decoration: none;
      color: var(--texto-suave); font-weight: 550; font-size: .9rem; }
    .secciones a:hover { color: var(--primario); }
    .secciones a.activa { color: var(--primario); border-bottom-color: var(--primario); }
  `,
})
export class AdminLayout {
  private readonly auth = inject(AuthService);
  readonly secciones = computed(() => SECCIONES_ADMIN.filter((s) => this.auth.tienePermiso(...s.permisos)));
}
