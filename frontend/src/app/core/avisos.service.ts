import { Injectable, signal } from '@angular/core';

export interface Aviso {
  id: number;
  tipo: 'exito' | 'error' | 'info';
  texto: string;
}

/** Mensajes emergentes. Se muestran con texto plano (nunca HTML) para no abrir una vía de XSS. */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private siguiente = 1;
  readonly avisos = signal<Aviso[]>([]);

  exito(texto: string) {
    this.agregar('exito', texto);
  }

  error(texto: string) {
    this.agregar('error', texto);
  }

  info(texto: string) {
    this.agregar('info', texto);
  }

  cerrar(id: number) {
    this.avisos.update((l) => l.filter((a) => a.id !== id));
  }

  private agregar(tipo: Aviso['tipo'], texto: string) {
    const id = this.siguiente++;
    this.avisos.update((l) => [...l.slice(-3), { id, tipo, texto }]);
    setTimeout(() => this.cerrar(id), tipo === 'error' ? 8000 : 4000);
  }
}
