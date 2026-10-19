import { Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AvisosService } from '../../core/avisos.service';
import { CatalogosService } from '../../core/catalogos.service';
import { ApiError, TIPOS_IDENTIFICACION } from '../../core/modelos';
import { CampoError } from '../../shared/campo-error';
import { aplicarErroresServidor, listoParaEnviar } from '../../shared/formularios';
import { correo, documentoValido, longitudRecortada, nombrePersona, razonSocial, sinHtml, telefonoColombia } from '../../shared/validadores';
import { UsuarioVista, UsuariosService } from './usuarios.service';

/** Registro (RF-01) y actualización (RF-02) de usuarios. Tipo de persona y documento no cambian después de creados. */
@Component({
  selector: 'app-formulario-usuario',
  imports: [ReactiveFormsModule, CampoError, RouterLink],
  template: `
    <h1>{{ existente() ? 'Editar usuario ' + existente()!.codigo : 'Registrar usuario' }}</h1>
    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate class="tarjeta">
      <div class="fila">
        <label>Tipo de persona
          <select formControlName="tipoPersona">
            <option value="NATURAL">Natural</option>
            <option value="JURIDICA">Jurídica</option>
          </select>
        </label>
        <label>Tipo de identificación
          <select formControlName="tipoIdentificacion">
            @for (t of tipos; track t.valor) { <option [value]="t.valor">{{ t.etiqueta }}</option> }
          </select>
        </label>
        <label>Número
          <input formControlName="numeroIdentificacion" maxlength="15"
            [placeholder]="existente() ? existente()!.identificacionMascara : ''" />
          <app-campo-error [control]="form.controls.numeroIdentificacion" />
        </label>
      </div>
      <label>{{ form.controls.tipoPersona.value === 'JURIDICA' ? 'Razón social' : 'Nombre completo' }}
        <input formControlName="nombreCompleto" maxlength="150" />
      </label>
      <app-campo-error [control]="form.controls.nombreCompleto" />
      <div class="fila">
        <label>Correo
          <input type="email" formControlName="correo" maxlength="254" />
          <app-campo-error [control]="form.controls.correo" />
        </label>
        <label>Teléfono
          <input formControlName="telefono" maxlength="13" placeholder="3001234567" />
          <app-campo-error [control]="form.controls.telefono" />
        </label>
      </div>
      <div class="fila">
        <label>Dirección de notificación
          <input formControlName="direccionNotificacion" maxlength="200" />
          <app-campo-error [control]="form.controls.direccionNotificacion" />
        </label>
        <label>Municipio
          <select formControlName="municipioId">
            <option [ngValue]="null" disabled>Seleccione</option>
            @for (m of municipios(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
          </select>
          <app-campo-error [control]="form.controls.municipioId" />
        </label>
      </div>
      @if (!existente()) {
        <label class="check">
          <input type="checkbox" formControlName="autorizaTratamientoDatos" />
          El usuario autoriza el tratamiento de sus datos personales (Ley 1581 de 2012)
        </label>
        <app-campo-error [control]="form.controls.autorizaTratamientoDatos" />
      }
      <div class="acciones">
        <button type="submit" [disabled]="enviando()">Guardar</button>
        <a class="boton secundario" routerLink="/usuarios">Cancelar</a>
      </div>
    </form>
  `,
})
export class FormularioUsuario {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly servicio = inject(UsuariosService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);

  readonly id = input<string>();
  readonly tipos = TIPOS_IDENTIFICACION;
  readonly municipios = toSignal(inject(CatalogosService).municipios(), { initialValue: [] });
  readonly existente = signal<UsuarioVista | null>(null);
  readonly enviando = signal(false);

  readonly form = this.fb.group(
    {
      tipoPersona: ['NATURAL', Validators.required],
      tipoIdentificacion: ['CC', Validators.required],
      numeroIdentificacion: ['', Validators.required],
      nombreCompleto: ['', [Validators.required, nombrePersona]],
      correo: ['', [Validators.required, correo]],
      telefono: ['', [Validators.required, telefonoColombia]],
      direccionNotificacion: ['', [Validators.required, longitudRecortada(5, 200), sinHtml]],
      municipioId: this.fb.control<number | null>(null, Validators.required),
      autorizaTratamientoDatos: [false, Validators.requiredTrue],
    },
    { validators: documentoValido() },
  );

  constructor() {
    // El nombre de una persona jurídica admite dígitos y signos de razón social.
    this.form.controls.tipoPersona.valueChanges.subscribe((tp) => {
      const c = this.form.controls.nombreCompleto;
      c.setValidators([Validators.required, tp === 'JURIDICA' ? razonSocial : nombrePersona]);
      c.updateValueAndValidity();
      this.form.controls.tipoIdentificacion.setValue(tp === 'JURIDICA' ? 'NIT' : 'CC');
    });
  }

  ngOnInit() {
    const id = this.id();
    if (id) {
      this.servicio.detalle(Number(id)).subscribe(({ usuario: u }) => {
        this.existente.set(u);
        this.form.patchValue({ ...u, numeroIdentificacion: '', autorizaTratamientoDatos: true });
        // Identidad inmutable: el número cifrado no se reenvía al editar.
        for (const c of ['tipoPersona', 'tipoIdentificacion', 'numeroIdentificacion', 'autorizaTratamientoDatos'] as const) {
          this.form.controls[c].disable();
        }
      });
    }
  }

  guardar() {
    if (!listoParaEnviar(this.form)) return;
    const v = this.form.getRawValue();
    const datos = {
      ...v,
      municipioId: v.municipioId!,
      numeroIdentificacion: v.numeroIdentificacion.trim().toUpperCase(),
      nombreCompleto: v.nombreCompleto.trim(),
      correo: v.correo.trim().toLowerCase(),
      direccionNotificacion: v.direccionNotificacion.trim(),
    };
    const u = this.existente();
    this.enviando.set(true);
    const op = u
      ? this.servicio.modificar(u.id, { nombreCompleto: datos.nombreCompleto, correo: datos.correo, telefono: datos.telefono,
          direccionNotificacion: datos.direccionNotificacion, municipioId: datos.municipioId }, u.version)
      : this.servicio.crear(datos);
    op.subscribe({
      next: (r) => {
        this.avisos.exito(u ? 'Usuario actualizado' : `Usuario ${r.codigo} registrado (Registraduría: ${r.estadoVerificacionRnec})`);
        if (r.advertencia) this.avisos.info(r.advertencia);
        this.router.navigate(['/usuarios', r.id]);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        aplicarErroresServidor(this.form, e);
        if (e.codigo === 'DUPLICADO') {
          this.form.controls.numeroIdentificacion.setErrors({ servidor: 'Ya existe un usuario con esa identificación' });
        }
      },
    });
  }
}
