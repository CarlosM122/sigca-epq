import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { CatalogosService } from '../../core/catalogos.service';
import { Pagina, TIPOS_IDENTIFICACION } from '../../core/modelos';
import { Paginador } from '../../shared/paginador';
import { sinHtml } from '../../shared/validadores';
import { UsuarioVista, UsuariosService } from './usuarios.service';

@Component({
  selector: 'app-lista-usuarios',
  imports: [ReactiveFormsModule, RouterLink, Paginador],
  template: `
    <div class="encabezado">
      <h1>Usuarios</h1>
      @if (auth.tienePermiso('USUARIO_CREAR')) { <a class="boton" routerLink="nuevo">Registrar usuario</a> }
    </div>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <input formControlName="texto" placeholder="Nombre o código" maxlength="100" />
      <select formControlName="tipoIdentificacion">
        <option value="">Tipo de documento</option>
        @for (t of tipos; track t.valor) { <option [value]="t.valor">{{ t.valor }}</option> }
      </select>
      <input formControlName="numeroIdentificacion" placeholder="Número exacto" maxlength="15" />
      <select formControlName="municipioId">
        <option [ngValue]="null">Todos los municipios</option>
        @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
      </select>
      <select formControlName="activo">
        <option [ngValue]="null">Activos e inactivos</option>
        <option [ngValue]="true">Activos</option>
        <option [ngValue]="false">Inactivos</option>
      </select>
      <button type="submit">Buscar</button>
    </form>
    <p class="ayuda">La identificación está cifrada: la búsqueda por número es exacta (el tipo es opcional).</p>
    <table class="tabla">
      <thead><tr><th>Código</th><th>Nombre</th><th>Identificación</th><th>Municipio</th><th>Estado</th></tr></thead>
      <tbody>
        @for (u of pagina()?.content ?? []; track u.id) {
          <tr>
            <td><a [routerLink]="[u.id]">{{ u.codigo }}</a></td>
            <td>{{ u.nombreCompleto }}</td>
            <td>{{ u.tipoIdentificacion }} {{ u.identificacionMascara }}</td>
            <td>{{ u.municipio }}</td>
            <td>{{ u.activo ? 'Activo' : 'Inactivo' }}</td>
          </tr>
        } @empty { <tr><td colspan="5">Sin resultados.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />
  `,
})
export class ListaUsuarios {
  private readonly servicio = inject(UsuariosService);
  readonly auth = inject(AuthService);
  readonly tipos = TIPOS_IDENTIFICACION;
  readonly pagina = signal<Pagina<UsuarioVista> | null>(null);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly filtros = this.fb.group({
    texto: ['', [Validators.maxLength(100), sinHtml]],
    tipoIdentificacion: [''],
    numeroIdentificacion: ['', Validators.maxLength(15)],
    municipioId: this.fb.control<number | null>(null),
    activo: this.fb.control<boolean | null>(null),
  });

  constructor() {
    this.buscar(0);
  }

  buscar(page: number) {
    if (this.filtros.invalid) return;
    const v = this.filtros.getRawValue();
    this.servicio
      .buscar({ ...v, numeroIdentificacion: v.numeroIdentificacion.trim().toUpperCase(), page })
      .subscribe((p) => this.pagina.set(p));
  }
}
