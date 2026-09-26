import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type TipoPersona = 'NATURAL' | 'JURIDICA';

export interface TipoDocumento {
  codigo: string;
  nombre: string;
  tipoPersona: TipoPersona;
}

export interface RegistroUsuarioRequest {
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string;
  razonSocial: string;
  email: string;
  telefono: string;
  password: string;
  confirmarPassword: string;
  aceptaTratamientoDatos: boolean;
}

export interface UsuarioResponse {
  id: number;
  tipoDocumento: string;
  numeroDocumento: string;
  nombre: string;
  email: string;
  estado: string; // ACTIVO | PENDIENTE_VALIDACION
}

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly api = 'http://localhost:8080/api'; // dirección del backend Spring Boot

  tiposDocumento(): Observable<TipoDocumento[]> {
    return this.http.get<TipoDocumento[]>(`${this.api}/tipos-documento`);
  }

  registrar(datos: RegistroUsuarioRequest): Observable<UsuarioResponse> {
    return this.http.post<UsuarioResponse>(`${this.api}/usuarios`, datos);
  }
}
