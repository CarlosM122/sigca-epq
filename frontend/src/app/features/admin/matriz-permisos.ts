import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { AvisosService } from '../../core/avisos.service';
import { AdminService, PermisoVista, RolVista } from './admin.service';

/**
 * Matriz rol-permiso (P-16). Se edita por rol; los cambios aplican a cada sesión cuando renueva su token.
 * Los permisos del portal son exclusivos del rol CIUDADANO, como valida el backend.
 */
@Component({
  selector: 'app-matriz-permisos',
  template: `
    <h1>Roles y permisos</h1>
    <p class="ayuda">Marque los permisos de cada rol y guarde la columna. Las sesiones abiertas toman el cambio al renovar su token (máximo 15 minutos).</p>
    <div class="texto-largo">
      <table class="tabla compacta">
        <thead>
          <tr>
            <th>Módulo</th><th>Permiso</th>
            @for (r of roles(); track r.codigo) { <th [title]="r.descripcion ?? ''">{{ r.nombre }}</th> }
          </tr>
        </thead>
        <tbody>
          @for (p of permisos(); track p.codigo) {
            <tr>
              <td>{{ p.modulo }}</td>
              <td [title]="p.descripcion">{{ p.codigo }}</td>
              @for (r of roles(); track r.codigo) {
                <td>
                  <input type="checkbox" [attr.aria-label]="r.codigo + ' ' + p.codigo" [checked]="tiene(r.codigo, p.codigo)"
                    [disabled]="!aplica(r, p)" (change)="alternar(r.codigo, p.codigo)" />
                </td>
              }
            </tr>
          }
        </tbody>
        <tfoot>
          <tr>
            <td colspan="2"></td>
            @for (r of roles(); track r.codigo) {
              <td><button type="button" class="secundario" [disabled]="!modificado(r.codigo) || guardando()" (click)="guardar(r)">Guardar</button></td>
            }
          </tr>
        </tfoot>
      </table>
    </div>
  `,
})
export class MatrizPermisos {
  private readonly servicio = inject(AdminService);
  private readonly avisos = inject(AvisosService);
  readonly roles = signal<RolVista[]>([]);
  readonly permisos = signal<PermisoVista[]>([]);
  /** Estado editado: rol -> permisos marcados. */
  readonly edicion = signal<Record<string, string[]>>({});
  readonly guardando = signal(false);
  private readonly original = computed(() => Object.fromEntries(this.roles().map((r) => [r.codigo, r.permisos])));

  constructor() {
    this.cargar();
  }

  cargar() {
    forkJoin([this.servicio.roles(), this.servicio.permisos()]).subscribe(([roles, permisos]) => {
      this.roles.set(roles);
      this.permisos.set(permisos);
      this.edicion.set(Object.fromEntries(roles.map((r) => [r.codigo, [...r.permisos]])));
    });
  }

  tiene(rol: string, permiso: string): boolean {
    return this.edicion()[rol]?.includes(permiso) ?? false;
  }

  aplica(r: RolVista, p: PermisoVista): boolean {
    if (r.codigo === 'ADMIN' && p.codigo === 'ROL_GESTIONAR') return false;
    return (r.codigo === 'CIUDADANO') === (p.modulo === 'PORTAL');
  }

  alternar(rol: string, permiso: string) {
    this.edicion.update((e) => {
      const actuales = e[rol] ?? [];
      return { ...e, [rol]: actuales.includes(permiso) ? actuales.filter((x) => x !== permiso) : [...actuales, permiso] };
    });
  }

  modificado(rol: string): boolean {
    const a = [...(this.original()[rol] ?? [])].sort().join();
    const b = [...(this.edicion()[rol] ?? [])].sort().join();
    return a !== b;
  }

  guardar(r: RolVista) {
    this.guardando.set(true);
    this.servicio.asignarPermisos(r.codigo, this.edicion()[r.codigo] ?? []).subscribe({
      next: (actualizado) => {
        this.guardando.set(false);
        this.roles.update((l) => l.map((x) => (x.codigo === actualizado.codigo ? actualizado : x)));
        this.avisos.exito(`Permisos del rol ${r.nombre} actualizados`);
      },
      error: () => this.guardando.set(false),
    });
  }
}
