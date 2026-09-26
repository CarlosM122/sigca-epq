import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators,
} from '@angular/forms';
import { TipoDocumento, TipoPersona, UsuarioResponse, UsuarioService } from './usuario.service';

/** Las dos contraseñas deben ser iguales (mismas reglas que valida el backend). */
const coincidenPasswords = (g: AbstractControl): ValidationErrors | null =>
  g.get('password')?.value === g.get('confirmarPassword')?.value ? null : { passwordsDistintas: true };

/** Mensaje cuando el campo está vacío. */
const OBLIGATORIO: Record<string, string> = {
  tipoDocumento: 'Seleccione el tipo de documento.',
  numeroDocumento: 'Ingrese el número de documento.',
  nombres: 'Ingrese sus nombres.',
  apellidos: 'Ingrese sus apellidos.',
  razonSocial: 'Ingrese la razón social.',
  email: 'Ingrese su correo electrónico.',
  telefono: 'Ingrese su teléfono.',
  password: 'Cree una contraseña.',
  confirmarPassword: 'Confirme la contraseña.',
  aceptaTratamientoDatos: 'Debe autorizar el tratamiento de datos personales para registrarse.',
};

/** Mensaje cuando el campo tiene datos con formato incorrecto. */
const FORMATO: Record<string, string> = {
  numeroDocumento: 'Use solo letras, números o guion (máximo 20 caracteres).',
  email: 'Escriba un correo válido, por ejemplo nombre@dominio.com.',
  telefono: 'Escriba entre 7 y 15 dígitos, sin espacios.',
  password: 'Use entre 8 y 72 caracteres, con letras y números.',
};

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, NgTemplateOutlet],
  templateUrl: './registro.html',
  styleUrl: './registro.css',
})
export class Registro {
  private readonly fb = inject(FormBuilder);
  private readonly usuarios = inject(UsuarioService);

  readonly tipos = signal<TipoDocumento[]>([]);
  readonly tipoPersona = signal<TipoPersona | null>(null);
  readonly enviando = signal(false);
  readonly errorCarga = signal(false);
  readonly errorGeneral = signal<string | null>(null);
  readonly registrado = signal<UsuarioResponse | null>(null);

  // Los nombres de los controles coinciden con los campos del JSON del backend.
  readonly form = this.fb.nonNullable.group(
    {
      tipoDocumento: ['', Validators.required],
      numeroDocumento: ['', [Validators.required, Validators.maxLength(20), Validators.pattern(/^[A-Za-z0-9-]+$/)]],
      nombres: [''],
      apellidos: [''],
      razonSocial: [''],
      email: ['', [Validators.required, Validators.maxLength(254), Validators.pattern(/^[^@\s]+@[^@\s]+\.[^@\s]+$/)]],
      telefono: ['', [Validators.required, Validators.pattern(/^[0-9]{7,15}$/)]],
      password: ['', [Validators.required, Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/)]],
      confirmarPassword: ['', Validators.required],
      aceptaTratamientoDatos: [false, Validators.requiredTrue],
    },
    { validators: coincidenPasswords },
  );

  constructor() {
    this.usuarios.tiposDocumento().subscribe({
      next: (t) => this.tipos.set(t),
      error: () => this.errorCarga.set(true),
    });

    // Al elegir el tipo de documento, el formulario pide nombres/apellidos o razón social.
    this.form.controls.tipoDocumento.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((codigo) => this.aplicarTipo(this.tipos().find((t) => t.codigo === codigo)));
  }

  private aplicarTipo(tipo?: TipoDocumento): void {
    const c = this.form.controls;
    const natural = tipo?.tipoPersona === 'NATURAL';
    const juridica = tipo?.tipoPersona === 'JURIDICA';

    c.nombres.setValidators(natural ? [Validators.required, Validators.maxLength(100)] : []);
    c.apellidos.setValidators(natural ? [Validators.required, Validators.maxLength(100)] : []);
    c.razonSocial.setValidators(juridica ? [Validators.required, Validators.maxLength(200)] : []);

    if (!natural) { c.nombres.reset(''); c.apellidos.reset(''); }
    if (!juridica) { c.razonSocial.reset(''); }
    [c.nombres, c.apellidos, c.razonSocial].forEach((x) => x.updateValueAndValidity());

    this.tipoPersona.set(tipo?.tipoPersona ?? null);
  }

  /** Mensaje de error de un campo, o null si no hay que mostrarlo. */
  error(nombre: string): string | null {
    const c = this.form.get(nombre);
    if (!c || !c.touched) return null;
    if (c.errors?.['servidor']) return c.errors['servidor'];
    if (c.errors?.['required'] || c.errors?.['requiredTrue']) return OBLIGATORIO[nombre] ?? 'Campo obligatorio.';
    if (c.errors) return FORMATO[nombre] ?? 'Revise este dato.';
    if (nombre === 'confirmarPassword' && this.form.errors?.['passwordsDistintas']) return 'Las contraseñas no coinciden.';
    return null;
  }

  enviar(): void {
    this.errorGeneral.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorGeneral.set('Revise los campos marcados antes de continuar.');
      return;
    }

    this.enviando.set(true);
    this.usuarios.registrar(this.form.getRawValue()).subscribe({
      next: (usuario) => {
        this.enviando.set(false);
        this.registrado.set(usuario);
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(false);
        const cuerpo = e.error;
        const campos = cuerpo?.errores ?? cuerpo?.properties?.errores; // errores por campo (400)
        if (e.status === 400 && campos) {
          Object.entries<string>(campos).forEach(([campo, msg]) => {
            this.form.get(campo)?.setErrors({ servidor: msg });
            this.form.get(campo)?.markAsTouched();
          });
          this.errorGeneral.set('Revise los campos marcados antes de continuar.');
        } else if (e.status === 0) {
          this.errorGeneral.set('No hay conexión con el servidor. Verifique que el backend esté en ejecución.');
        } else {
          this.errorGeneral.set(cuerpo?.detail ?? 'No fue posible completar el registro. Intente de nuevo.');
        }
      },
    });
  }

  nuevoRegistro(): void {
    this.form.reset();
    this.aplicarTipo(undefined);
    this.errorGeneral.set(null);
    this.registrado.set(null);
  }
}
