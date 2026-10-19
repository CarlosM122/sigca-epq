import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { AvisosService } from '../../core/avisos.service';
import { ApiError } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { correo, longitudRecortada, sinHtml, telefonoColombia } from '../../shared/validadores';
import { ContratoVista } from '../contratos/contratos.service';
import { FormularioContrato } from '../contratos/formulario-contrato';
import { PredioVista, UsuarioVista, UsuariosService } from './usuarios.service';

/**
 * Ficha del usuario (RF-02, RF-03): datos con la identificación enmascarada, predios y contratos.
 * La suspensión y la reconexión no se hacen aquí: van por órdenes de servicio (P-11).
 */
@Component({
  selector: 'app-detalle-usuario',
  imports: [RouterLink, DatePipe, ReactiveFormsModule, CampoError, FormularioContrato],
  template: `
    <p><a routerLink="/usuarios">← Usuarios</a></p>
    @if (usuario(); as u) {
      <div class="encabezado">
        <h1>{{ u.nombreCompleto }} <small>{{ u.codigo }}</small></h1>
        @if (auth.tienePermiso('USUARIO_MODIFICAR') && u.activo) { <a class="boton secundario" routerLink="editar">Editar</a> }
      </div>
      <dl class="ficha">
        <dt>Identificación</dt>
        <dd>
          {{ u.tipoIdentificacion }} {{ u.identificacionMascara }} · {{ u.tipoPersona === 'JURIDICA' ? 'Persona jurídica' : 'Persona natural' }}
        </dd>
        <dt>Registraduría</dt>
        <dd>
          <span class="estado">{{ u.estadoVerificacionRnec }}</span>
          @if (u.fechaVerificacionRnec) { {{ u.fechaVerificacionRnec | date: 'short' }} }
          @if (u.estadoVerificacionRnec === 'PENDIENTE' && auth.tienePermiso('USUARIO_MODIFICAR')) {
            <button type="button" class="enlace" (click)="reverificar(u)">Verificar ahora</button>
          }
        </dd>
        <dt>Contacto</dt><dd>{{ u.correo }} · {{ u.telefono }}</dd>
        <dt>Dirección</dt><dd>{{ u.direccionNotificacion }}, {{ u.municipio }}</dd>
        <dt>Portal</dt><dd>{{ u.cuentaPortal }}</dd>
        <dt>Estado</dt>
        <dd>
          <span class="estado" [attr.data-estado]="u.activo ? 'ACTIVO' : 'CANCELADO'">{{ u.activo ? 'Activo' : 'Inactivo' }}</span>
          @if (!u.activo) { desde {{ u.fechaInactivacion | date: 'mediumDate' }}: {{ u.motivoInactivacion }} }
        </dd>
      </dl>

      @if (auth.tienePermiso('USUARIO_MODIFICAR') && u.activo) {
        <details>
          <summary>Actualizar datos de contacto</summary>
          <form class="tarjeta" [formGroup]="contacto" (ngSubmit)="guardarContacto(u)" novalidate>
            <div class="fila">
              <label>Correo <input type="email" formControlName="correo" maxlength="254" />
                <app-campo-error [control]="contacto.controls.correo" /></label>
              <label>Teléfono <input formControlName="telefono" maxlength="13" />
                <app-campo-error [control]="contacto.controls.telefono" /></label>
            </div>
            <label>Dirección de notificación <input formControlName="direccionNotificacion" maxlength="200" /></label>
            <app-campo-error [control]="contacto.controls.direccionNotificacion" />
            <button type="submit">Guardar contacto</button>
          </form>
        </details>
      }

      <h2>Predios</h2>
      <p class="ayuda">Predios donde el usuario tiene o tuvo contratos.</p>
      <table class="tabla">
        <thead><tr><th>Código</th><th>Dirección</th><th>Municipio</th><th>Sector</th><th>Uso</th><th>Estrato</th></tr></thead>
        <tbody>
          @for (p of predios(); track p.id) {
            <tr>
              <td><a [routerLink]="['/contratos/predios', p.id]">{{ p.codigoPredio }}</a></td>
              <td>{{ p.direccion }}{{ p.barrioVereda ? ', ' + p.barrioVereda : '' }}</td>
              <td>{{ p.municipio }}</td><td>{{ p.sector ?? '—' }}</td><td>{{ p.uso }}</td><td>{{ p.estrato ?? '—' }}</td>
            </tr>
          } @empty { <tr><td colspan="6">Sin predios.</td></tr> }
        </tbody>
      </table>

      <h2>Contratos</h2>
      <table class="tabla">
        <thead><tr><th>Número</th><th>Servicio</th><th>Predio</th><th>Medidor</th><th>Vinculación</th><th>Estado</th></tr></thead>
        <tbody>
          @for (c of contratos(); track c.id) {
            <tr>
              <td><a [routerLink]="['/contratos', c.id]">{{ c.numeroContrato }}</a></td>
              <td>{{ c.tipoServicio }}</td><td>{{ c.codigoPredio }} · {{ c.direccion }}</td>
              <td>{{ c.serialMedidor ?? '—' }}</td><td>{{ c.fechaVinculacion | date: 'mediumDate' }}</td>
              <td><span class="estado" [attr.data-estado]="c.estado">{{ c.estado }}</span></td>
            </tr>
          } @empty { <tr><td colspan="6">Sin contratos.</td></tr> }
        </tbody>
      </table>
      @if (auth.tienePermiso('CONTRATO_CREAR') && u.activo) {
        <details><summary>Registrar contrato</summary>
          <div class="tarjeta">
            <app-formulario-contrato [usuarioId]="u.id" [predios]="predios()" [contratos]="contratos()" (creado)="recargar()" />
          </div>
        </details>
      }

      @if (auth.tienePermiso('USUARIO_INACTIVAR') && u.activo) {
        <details class="zona-peligro"><summary>Inactivar usuario</summary>
          <form [formGroup]="inactivacion" (ngSubmit)="inactivar(u)" novalidate>
            <p>Solo es posible sin contratos vigentes, sin deuda y sin acuerdos de pago activos.</p>
            <input formControlName="motivo" placeholder="Motivo" maxlength="300" />
            <app-campo-error [control]="inactivacion.controls.motivo" />
            <button type="submit" class="peligro">Inactivar</button>
          </form>
        </details>
      }
    }
  `,
})
export class DetalleUsuario {
  private readonly servicio = inject(UsuariosService);
  private readonly avisos = inject(AvisosService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);

  readonly id = input.required<string>();
  readonly usuario = signal<UsuarioVista | null>(null);
  readonly predios = signal<PredioVista[]>([]);
  readonly contratos = signal<ContratoVista[]>([]);

  readonly contacto = this.fb.group({
    correo: ['', [Validators.required, correo]],
    telefono: ['', [Validators.required, telefonoColombia]],
    direccionNotificacion: ['', [Validators.required, longitudRecortada(5, 200), sinHtml]],
  });
  readonly inactivacion = this.fb.group({ motivo: ['', [Validators.required, longitudRecortada(5, 300), sinHtml]] });

  ngOnInit() {
    this.recargar();
  }

  recargar() {
    this.servicio.detalle(Number(this.id())).subscribe((d) => {
      this.mostrar(d.usuario);
      this.predios.set(d.predios);
      this.contratos.set(d.contratos);
    });
  }

  private mostrar(u: UsuarioVista) {
    this.usuario.set(u);
    this.contacto.reset({ correo: u.correo, telefono: u.telefono, direccionNotificacion: u.direccionNotificacion });
  }

  guardarContacto(u: UsuarioVista) {
    if (!listoParaEnviar(this.contacto)) return;
    const v = this.contacto.getRawValue();
    this.servicio
      .actualizarContacto(u.id, { correo: v.correo.trim().toLowerCase(), telefono: v.telefono.trim(),
        direccionNotificacion: v.direccionNotificacion.trim() }, u.version)
      .subscribe({
        next: (r) => {
          this.mostrar(r);
          this.avisos.exito('Datos de contacto actualizados');
        },
        error: (e: ApiError) => aplicarErroresServidor(this.contacto, e),
      });
  }

  reverificar(u: UsuarioVista) {
    this.servicio.reverificar(u.id).subscribe((r) => {
      this.mostrar(r);
      this.avisos[r.estadoVerificacionRnec === 'PENDIENTE' ? 'info' : 'exito'](
        r.advertencia ?? `Registraduría: ${r.estadoVerificacionRnec}`,
      );
    });
  }

  inactivar(u: UsuarioVista) {
    if (!listoParaEnviar(this.inactivacion)) return;
    this.servicio.inactivar(u.id, this.inactivacion.getRawValue().motivo.trim(), u.version).subscribe((r) => {
      this.mostrar(r);
      this.avisos.exito('Usuario inactivado');
    });
  }
}
