import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { API } from './api';
import { Municipio } from './modelos';

@Injectable({ providedIn: 'root' })
export class CatalogosService {
  private readonly http = inject(HttpClient);
  private municipios$?: Observable<Municipio[]>;

  /** Municipios atendidos por EPQ (se consultan una sola vez por sesión). */
  municipios(): Observable<Municipio[]> {
    this.municipios$ ??= this.http.get<Municipio[]>(`${API}/publico/municipios`).pipe(shareReplay(1));
    return this.municipios$;
  }
}
