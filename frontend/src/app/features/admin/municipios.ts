import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { longitudRecortada, sinHtml } from '../../shared/validadores';
import { AdminService, MunicipioAdmin, SectorVista, TipoSector } from './admin.service';

export const TIPOS_SECTOR: { valor: TipoSector; etiqueta: string }[] = [
  { valor: 'ZONA_RURAL', etiqueta: 'Zona rural' },
  { valor: 'CORREGIMIENTO', etiqueta: 'Corregimiento' },
  { valor: 'VEREDA', etiqueta: 'Vereda' },
  { valor: 'URBANO', etiqueta: 'Urbano' },
];

/** P-02: municipios atendidos (activar o desactivar) y sus sectores. */
@Component({
  selector: 'app-municipios',
  imports: [ReactiveFormsModule, CampoError],
  template: `
    <h1>Municipios y sectores</h1>
    <div class="columnas">
      <section class="tarjeta">
        <h2>Municipios</h2>
        <table class="tabla">
          <thead><tr><th>DANE</th><th>Municipio</th><th>Estado</th><th></th></tr></thead>
          <tbody>
            @for (m of municipios(); track m.id) {
              <tr [class.alerta]="seleccionado()?.id === m.id">
                <td>{{ m.codigoDane }}</td>
                <td><button type="button" class="enlace" (click)="seleccionar(m)">{{ m.nombre }}</button></td>
                <td>{{ m.activo ? 'Atendido' : 'Inactivo' }}</td>
                <td><button type="button" class="enlace" (click)="estadoMunicipio(m)">{{ m.activo ? 'Desactivar' : 'Activar' }}</button></td>
              </tr>
            }
          </tbody>
        </table>
      </section>

      @if (seleccionado(); as m) {
        <section class="tarjeta">
          <h2>Sectores de {{ m.nombre }}</h2>
          <form [formGroup]="form" (ngSubmit)="guardarSector()" novalidate>
            <div class="fila">
              <label>Nombre <input formControlName="nombre" maxlength="80" /></label>
              <label>Tipo
                <select formControlName="tipo">
                  @for (t of tipos; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
                </select>
              </label>
            </div>
            <app-campo-error [control]="form.controls.nombre" />
            <div class="acciones">
              <button type="submit" [disabled]="enviando()">{{ editando() ? 'Guardar cambios' : 'Agregar sector' }}</button>
              @if (editando()) { <button type="button" class="secundario" (click)="cancelar()">Cancelar</button> }
            </div>
          </form>
          <table class="tabla">
            <thead><tr><th>Sector</th><th>Tipo</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (s of sectores(); track s.id) {
                <tr>
                  <td>{{ s.nombre }}</td><td>{{ etiqueta(s.tipo) }}</td><td>{{ s.activo ? 'Activo' : 'Inactivo' }}</td>
                  <td>
                    <button type="button" class="enlace" (click)="editar(s)">Editar</button>
                    <button type="button" class="enlace" (click)="estadoSector(s)">{{ s.activo ? 'Desactivar' : 'Activar' }}</button>
                  </td>
                </tr>
              } @empty { <tr><td colspan="4">Sin sectores.</td></tr> }
            </tbody>
          </table>
        </section>
      }
    </div>
  `,
})
export class Municipios {
  private readonly servicio = inject(AdminService);
  private readonly avisos = inject(AvisosService);
  readonly tipos = TIPOS_SECTOR;
  readonly municipios = signal<MunicipioAdmin[]>([]);
  readonly seleccionado = signal<MunicipioAdmin | null>(null);
  readonly sectores = signal<SectorVista[]>([]);
  readonly editando = signal<SectorVista | null>(null);
  readonly enviando = signal(false);
  readonly form = inject(NonNullableFormBuilder).group({
    nombre: ['', [Validators.required, longitudRecortada(3, 80), sinHtml]],
    tipo: this.tipos[0].valor as TipoSector,
  });

  constructor() {
    this.servicio.municipios().subscribe((l) => this.municipios.set(l));
  }

  etiqueta(tipo: TipoSector): string {
    return this.tipos.find((t) => t.valor === tipo)?.etiqueta ?? tipo;
  }

  seleccionar(m: MunicipioAdmin) {
    this.seleccionado.set(m);
    this.cancelar();
    this.servicio.sectores(m.id).subscribe((l) => this.sectores.set(l));
  }

  estadoMunicipio(m: MunicipioAdmin) {
    if (m.activo && !confirm(`¿Desactivar ${m.nombre}? No se podrán registrar nuevos usuarios, predios ni funcionarios en él.`)) return;
    this.servicio.estadoMunicipio(m.id, !m.activo).subscribe((n) => {
      this.municipios.update((l) => l.map((x) => (x.id === n.id ? n : x)));
      this.avisos.exito(`${n.nombre} ${n.activo ? 'activado' : 'desactivado'}`);
    });
  }

  editar(s: SectorVista) {
    this.editando.set(s);
    this.form.reset({ nombre: s.nombre, tipo: s.tipo });
  }

  cancelar() {
    this.editando.set(null);
    this.form.reset({ nombre: '', tipo: this.tipos[0].valor });
  }

  guardarSector() {
    const m = this.seleccionado();
    if (!m || !listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    const s = this.editando();
    this.enviando.set(true);
    const peticion = s
      ? this.servicio.modificarSector(s.id, v.nombre.trim(), v.tipo)
      : this.servicio.crearSector(m.id, v.nombre.trim(), v.tipo);
    peticion.subscribe({
      next: () => {
        this.enviando.set(false);
        this.avisos.exito(s ? 'Sector actualizado' : 'Sector creado');
        this.seleccionar(m);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        aplicarErroresServidor(this.form, e);
      },
    });
  }

  estadoSector(s: SectorVista) {
    this.servicio.estadoSector(s.id, !s.activo).subscribe((n) => this.sectores.update((l) => l.map((x) => (x.id === n.id ? n : x))));
  }
}
