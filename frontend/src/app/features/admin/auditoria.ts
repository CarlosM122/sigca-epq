import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Pagina } from '../../core/modelos';
import { hoyIso } from '../../shared/formularios';
import { Paginador } from '../../shared/paginador';
import { AdminService, RegistroAuditoria } from './admin.service';

function haceDias(dias: number): string {
  const d = new Date();
  d.setDate(d.getDate() - dias);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** RF-15: consulta del log de auditoría (solo lectura; la BD impide modificarlo). Máximo un año por consulta. */
@Component({
  selector: 'app-auditoria',
  imports: [ReactiveFormsModule, DatePipe, Paginador],
  template: `
    <h1>Auditoría</h1>
    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <select formControlName="operacion" aria-label="Operación">
        <option value="">Todas las operaciones</option>
        @for (o of operaciones(); track o) { <option [value]="o">{{ o }}</option> }
      </select>
      <input formControlName="usuario" placeholder="Usuario funcionario" maxlength="60" />
      <input formControlName="entidad" placeholder="Entidad (p. ej. FACTURA)" maxlength="30" />
      <input formControlName="entidadId" type="number" min="1" placeholder="Id" />
      <select formControlName="resultado" aria-label="Resultado">
        <option value="">Éxito y fallo</option><option value="EXITO">Éxito</option><option value="FALLO">Fallo</option>
      </select>
      <label>Desde <input type="date" formControlName="desde" /></label>
      <label>Hasta <input type="date" formControlName="hasta" /></label>
      <button type="submit" [disabled]="filtros.invalid">Buscar</button>
    </form>
    <table class="tabla compacta">
      <thead><tr><th>Fecha</th><th>Actor</th><th>Operación</th><th>Entidad</th><th>Resultado</th><th>IP</th><th>Detalle</th></tr></thead>
      <tbody>
        @for (r of pagina()?.content ?? []; track r.id) {
          <tr [class.alerta]="r.resultado === 'FALLO'">
            <td>{{ r.fecha | date: 'medium' }}</td><td>{{ r.actorTipo }} · {{ r.actor }}</td><td>{{ r.operacion }}</td>
            <td>{{ r.entidad }} {{ r.entidadId }}</td><td>{{ r.resultado }}</td><td>{{ r.ipOrigen }}</td>
            <td><code>{{ r.detalle }}</code></td>
          </tr>
        } @empty { <tr><td colspan="7">Sin registros.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />
  `,
})
export class Auditoria {
  private readonly servicio = inject(AdminService);
  readonly operaciones = toSignal(this.servicio.operacionesAuditables(), { initialValue: [] });
  readonly pagina = signal<Pagina<RegistroAuditoria> | null>(null);
  readonly filtros = inject(NonNullableFormBuilder).group({
    operacion: [''],
    usuario: ['', Validators.pattern(/^[a-z0-9._-]{0,60}$/)],
    entidad: ['', Validators.pattern(/^[A-Za-z_]{0,30}$/)],
    entidadId: [''],
    resultado: [''],
    desde: [haceDias(30), Validators.required],
    hasta: [hoyIso(), Validators.required],
  });

  constructor() {
    this.buscar(0);
  }

  buscar(page: number) {
    const v = this.filtros.getRawValue();
    this.servicio
      .auditoria({
        ...v,
        usuario: v.usuario.trim().toLowerCase(),
        entidad: v.entidad.trim().toUpperCase(),
        entidadId: v.entidadId ? Number(v.entidadId) : null,
        page,
      })
      .subscribe((p) => this.pagina.set(p));
  }
}
