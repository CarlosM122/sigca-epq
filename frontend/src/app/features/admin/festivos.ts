import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, hoyIso, listoParaEnviar } from '../../shared/formularios';
import { longitudRecortada, sinHtml } from '../../shared/validadores';
import { AdminService, FestivoVista } from './admin.service';

/** P-09: calendario de festivos para los días hábiles. Un festivo pasado no se elimina (ya se usó en plazos). */
@Component({
  selector: 'app-festivos',
  imports: [ReactiveFormsModule, CampoError, DatePipe],
  template: `
    <div class="encabezado">
      <h1>Festivos {{ anio() }}</h1>
      <div class="acciones">
        <button type="button" class="secundario" (click)="cambiarAnio(-1)">← {{ anio() - 1 }}</button>
        <button type="button" class="secundario" (click)="cambiarAnio(1)">{{ anio() + 1 }} →</button>
      </div>
    </div>
    <form class="filtros" [formGroup]="form" (ngSubmit)="agregar()" novalidate>
      <label>Fecha <input type="date" formControlName="fecha" /></label>
      <label>Descripción <input formControlName="descripcion" maxlength="100" /></label>
      <button type="submit" [disabled]="enviando()">Agregar</button>
    </form>
    <app-campo-error [control]="form.controls.fecha" />
    <app-campo-error [control]="form.controls.descripcion" />
    <table class="tabla">
      <thead><tr><th>Fecha</th><th>Descripción</th><th></th></tr></thead>
      <tbody>
        @for (f of festivos(); track f.fecha) {
          <tr>
            <td>{{ f.fecha + 'T00:00:00' | date: 'EEEE d MMMM' }}</td>
            <td>{{ f.descripcion }}</td>
            <td>@if (f.fecha >= hoy) { <button type="button" class="enlace" (click)="eliminar(f)">Eliminar</button> }</td>
          </tr>
        } @empty { <tr><td colspan="3">No hay festivos registrados para {{ anio() }}.</td></tr> }
      </tbody>
    </table>
  `,
})
export class Festivos {
  private readonly servicio = inject(AdminService);
  private readonly avisos = inject(AvisosService);
  readonly hoy = hoyIso();
  readonly anio = signal(new Date().getFullYear());
  readonly festivos = signal<FestivoVista[]>([]);
  readonly enviando = signal(false);
  readonly form = inject(NonNullableFormBuilder).group({
    fecha: ['', Validators.required],
    descripcion: ['', [Validators.required, longitudRecortada(3, 100), sinHtml]],
  });

  constructor() {
    this.cargar();
  }

  cambiarAnio(delta: number) {
    this.anio.update((a) => a + delta);
    this.cargar();
  }

  cargar() {
    this.servicio.festivos(this.anio()).subscribe((l) => this.festivos.set(l));
  }

  agregar() {
    if (!listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    this.enviando.set(true);
    this.servicio.agregarFestivo(v.fecha, v.descripcion.trim()).subscribe({
      next: (f) => {
        this.enviando.set(false);
        this.form.reset();
        this.avisos.exito('Festivo agregado');
        if (f.fecha.startsWith(String(this.anio()))) this.cargar();
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        aplicarErroresServidor(this.form, e);
      },
    });
  }

  eliminar(f: FestivoVista) {
    if (!confirm(`¿Eliminar el festivo del ${f.fecha}?`)) return;
    this.servicio.eliminarFestivo(f.fecha).subscribe(() => {
      this.avisos.exito('Festivo eliminado');
      this.cargar();
    });
  }
}
