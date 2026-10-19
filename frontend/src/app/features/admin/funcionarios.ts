import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { Observable, concat, last, of } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { CatalogosService } from '../../core/catalogos.service';
import { ApiError, Pagina } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { Paginador } from '../../shared/paginador';
import { correo, nombrePersona, telefonoColombia } from '../../shared/validadores';
import { AdminService, FuncionarioVista } from './admin.service';

/** RF-14: todo funcionario tiene al menos un rol y un Gestor de PQR necesita municipio (RN-20). */
const reglasRoles: ValidatorFn = (g) => {
  const roles = g.get('roles')!.value as string[];
  if (!roles.length) return { sinRol: true };
  return roles.includes('GESTOR_PQR') && !g.get('municipioId')!.value ? { gestorSinMunicipio: true } : null;
};

@Component({
  selector: 'app-funcionarios',
  imports: [ReactiveFormsModule, CampoError, Paginador, DatePipe],
  template: `
    <div class="encabezado">
      <h1>Funcionarios</h1>
      @if (!formularioAbierto()) { <button type="button" (click)="abrirNuevo()">Crear funcionario</button> }
    </div>

    @if (formularioAbierto()) {
      <section class="tarjeta">
        <h2>{{ editando() ? 'Editar ' + editando()!.username : 'Nuevo funcionario' }}</h2>
        <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
          <div class="fila">
            <label>Código <input formControlName="codigoFuncionario" maxlength="20" placeholder="EPQ-0001" /></label>
            <label>Usuario <input formControlName="username" maxlength="60" autocomplete="off" placeholder="nombre.apellido" /></label>
          </div>
          <app-campo-error [control]="form.controls.codigoFuncionario" />
          <app-campo-error [control]="form.controls.username" />
          <label>Nombre completo <input formControlName="nombreCompleto" maxlength="150" /></label>
          <app-campo-error [control]="form.controls.nombreCompleto" />
          <div class="fila">
            <label>Correo <input type="email" formControlName="correo" maxlength="254" /></label>
            <label>Teléfono <input formControlName="telefono" maxlength="13" /></label>
          </div>
          <app-campo-error [control]="form.controls.correo" />
          <app-campo-error [control]="form.controls.telefono" />
          <label>Municipio
            <select formControlName="municipioId">
              <option [ngValue]="null">Sin municipio</option>
              @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
            </select>
          </label>
          <fieldset [disabled]="!puedeAsignarRoles()">
            <legend>Roles @if (!puedeAsignarRoles()) { <small>(requiere permiso ROL_GESTIONAR)</small> }</legend>
            @for (r of rolesAsignables(); track r.codigo) {
              <label class="check" [title]="r.descripcion ?? ''">
                <input type="checkbox" [checked]="form.controls.roles.value.includes(r.codigo)" (change)="alternarRol(r.codigo)" />
                {{ r.nombre }}
              </label>
            }
          </fieldset>
          @if (form.errors?.['sinRol'] && form.touched) { <small class="error">Asigne al menos un rol</small> }
          @if (form.errors?.['gestorSinMunicipio']) { <small class="error">Un Gestor de PQR debe tener municipio (RN-20)</small> }
          @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
          <div class="acciones">
            <button type="submit" [disabled]="enviando()">Guardar</button>
            <button type="button" class="secundario" (click)="cancelar()">Cancelar</button>
          </div>
        </form>
      </section>
    }

    @if (passwordTemporal(); as p) {
      <div class="exito-caja" role="status">
        Contraseña temporal de <strong>{{ p.username }}</strong>: <code>{{ p.password }}</code>. Entréguela por un canal seguro;
        no se volverá a mostrar. En el primer ingreso deberá registrar su app autenticadora y cambiarla.
        <button type="button" class="enlace" (click)="passwordTemporal.set(null)">Ocultar</button>
      </div>
    }

    <form class="filtros" [formGroup]="filtros" (ngSubmit)="buscar(0)">
      <input formControlName="texto" placeholder="Usuario, nombre, correo o código" maxlength="60" />
      <select formControlName="activo">
        <option [ngValue]="null">Todos</option>
        <option [ngValue]="true">Activos</option>
        <option [ngValue]="false">Inactivos</option>
      </select>
      <button type="submit">Buscar</button>
    </form>

    <table class="tabla">
      <thead><tr><th>Usuario</th><th>Nombre</th><th>Roles</th><th>Municipio</th><th>Estado</th><th>Último acceso</th><th></th></tr></thead>
      <tbody>
        @for (f of pagina()?.content ?? []; track f.id) {
          <tr>
            <td>{{ f.username }}<br /><small>{{ f.codigoFuncionario }}</small></td>
            <td>{{ f.nombreCompleto }}<br /><small>{{ f.correo }}</small></td>
            <td>{{ f.roles.join(', ') || '—' }}</td>
            <td>{{ f.municipio ?? '—' }}</td>
            <td>
              {{ f.activo ? 'Activo' : 'Inactivo' }}
              @if (f.bloqueadoHasta) { <span class="etiqueta peligro">Bloqueado</span> }
              @if (!f.dobleFactorActivo) { <span class="etiqueta">Sin 2FA</span> }
              @if (f.cambioPasswordRequerido) { <span class="etiqueta">Clave temporal</span> }
            </td>
            <td>{{ f.ultimoAcceso ? (f.ultimoAcceso | date: 'short') : '—' }}</td>
            <td>
              <button type="button" class="enlace" (click)="editar(f)">Editar</button>
              <button type="button" class="enlace" (click)="cambiarEstado(f)">{{ f.activo ? 'Desactivar' : 'Activar' }}</button>
              @if (f.bloqueadoHasta) { <button type="button" class="enlace" (click)="desbloquear(f)">Desbloquear</button> }
              @if (f.dobleFactorActivo) { <button type="button" class="enlace" (click)="reiniciarDobleFactor(f)">Reiniciar 2FA</button> }
              <button type="button" class="enlace" (click)="restablecer(f)">Clave temporal</button>
            </td>
          </tr>
        } @empty { <tr><td colspan="7">Sin funcionarios.</td></tr> }
      </tbody>
    </table>
    <app-paginador [pagina]="pagina()" (cambiar)="buscar($event)" />
  `,
})
export class Funcionarios {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(AdminService);
  private readonly avisos = inject(AvisosService);
  private readonly auth = inject(AuthService);
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  private readonly roles = toSignal(this.servicio.roles(), { initialValue: [] });
  readonly rolesAsignables = computed(() => this.roles().filter((r) => r.codigo !== 'CIUDADANO' && r.activo));
  readonly puedeAsignarRoles = computed(() => this.auth.tienePermiso('ROL_GESTIONAR'));
  readonly pagina = signal<Pagina<FuncionarioVista> | null>(null);
  readonly editando = signal<FuncionarioVista | null>(null);
  readonly nuevo = signal(false);
  readonly formularioAbierto = computed(() => this.nuevo() || !!this.editando());
  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);
  readonly passwordTemporal = signal<{ username: string; password: string } | null>(null);

  readonly filtros = this.fb.group({ texto: [''], activo: this.fb.control<boolean | null>(true) });

  readonly form = this.fb.group(
    {
      codigoFuncionario: ['', [Validators.required, Validators.pattern(/^[A-Z0-9-]{3,20}$/)]],
      username: ['', [Validators.required, Validators.pattern(/^[a-z0-9._-]{3,60}$/)]],
      nombreCompleto: ['', [Validators.required, nombrePersona]],
      correo: ['', [Validators.required, correo]],
      telefono: ['', telefonoColombia],
      municipioId: this.fb.control<number | null>(null),
      roles: this.fb.control<string[]>([]),
    },
    { validators: reglasRoles },
  );

  constructor() {
    if (!this.puedeAsignarRoles()) {
      // Sin ROL_GESTIONAR se crean sin roles; el Administrador los asigna después
      this.form.removeValidators(reglasRoles);
    }
    this.buscar(0);
  }

  buscar(page: number) {
    const v = this.filtros.getRawValue();
    this.servicio.funcionarios({ texto: v.texto.trim(), activo: v.activo, page }).subscribe((p) => this.pagina.set(p));
  }

  abrirNuevo() {
    this.cancelar();
    this.nuevo.set(true);
  }

  alternarRol(codigo: string) {
    const actuales = this.form.controls.roles.value;
    this.form.controls.roles.setValue(actuales.includes(codigo) ? actuales.filter((r) => r !== codigo) : [...actuales, codigo]);
    this.form.markAsTouched();
  }

  editar(f: FuncionarioVista) {
    this.cancelar();
    this.editando.set(f);
    this.form.reset({ ...f, telefono: f.telefono ?? '', municipioId: f.municipioId ?? null, roles: [...f.roles] });
    this.form.controls.codigoFuncionario.disable();
    this.form.controls.username.disable();
  }

  cancelar() {
    this.editando.set(null);
    this.nuevo.set(false);
    this.error.set(null);
    this.enviando.set(false);
    this.form.reset({ roles: [], municipioId: null });
    this.form.controls.codigoFuncionario.enable();
    this.form.controls.username.enable();
  }

  guardar() {
    if (!listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    const datos = {
      nombreCompleto: v.nombreCompleto.trim().replace(/\s+/g, ' '),
      correo: v.correo.trim().toLowerCase(),
      telefono: v.telefono.trim() || null,
      municipioId: v.municipioId,
    };
    const f = this.editando();
    this.enviando.set(true);
    this.error.set(null);
    const error = (e: ApiError) => {
      this.enviando.set(false);
      if (!aplicarErroresServidor(this.form, e)) this.error.set(e.mensaje);
    };
    if (!f) {
      this.servicio
        .crear({ ...datos, codigoFuncionario: v.codigoFuncionario.trim(), username: v.username.trim(), roles: v.roles })
        .subscribe({
          next: (r) => {
            this.passwordTemporal.set({ username: r.funcionario.username, password: r.passwordTemporal });
            this.avisos.exito(`Funcionario ${r.funcionario.username} creado`);
            this.cancelar();
            this.buscar(0);
          },
          error,
        });
      return;
    }
    const cambioRoles = this.puedeAsignarRoles() && [...v.roles].sort().join() !== [...f.roles].sort().join();
    const pasoDatos = this.servicio.actualizar(f.id, { ...datos, version: f.version });
    const pasoRoles: Observable<unknown> = cambioRoles ? this.servicio.asignarRoles(f.id, v.roles) : of(null);
    // Si se quita el municipio, primero se quitan los roles que lo exigen; si se agrega, primero el municipio
    const pasos = !datos.municipioId && f.municipioId ? concat(pasoRoles, pasoDatos) : concat(pasoDatos, pasoRoles);
    pasos.pipe(last()).subscribe({
      next: () => {
        this.avisos.exito('Funcionario actualizado');
        this.cancelar();
        this.buscar(this.pagina()?.page.number ?? 0);
      },
      error: (e: ApiError) => {
        error(e);
        this.buscar(this.pagina()?.page.number ?? 0);
      },
    });
  }

  cambiarEstado(f: FuncionarioVista) {
    if (f.activo && !confirm(`¿Desactivar a ${f.username}? No podrá ingresar al sistema.`)) return;
    this.servicio.cambiarEstado(f.id, !f.activo).subscribe(() => this.recargar(f.activo ? 'Funcionario desactivado' : 'Funcionario activado'));
  }

  desbloquear(f: FuncionarioVista) {
    this.servicio.desbloquear(f.id).subscribe(() => this.recargar('Cuenta desbloqueada'));
  }

  reiniciarDobleFactor(f: FuncionarioVista) {
    if (!confirm(`¿Reiniciar el segundo factor de ${f.username}? Deberá registrar de nuevo su app autenticadora.`)) return;
    this.servicio.reiniciarDobleFactor(f.id).subscribe(() => this.recargar('Segundo factor reiniciado'));
  }

  restablecer(f: FuncionarioVista) {
    if (!confirm(`¿Generar una contraseña temporal para ${f.username}? La actual dejará de servir.`)) return;
    this.servicio.restablecerPassword(f.id).subscribe((r) => {
      this.passwordTemporal.set({ username: r.funcionario.username, password: r.passwordTemporal });
      this.recargar('Contraseña temporal generada');
    });
  }

  private recargar(mensaje: string) {
    this.avisos.exito(mensaje);
    this.buscar(this.pagina()?.page.number ?? 0);
  }
}
