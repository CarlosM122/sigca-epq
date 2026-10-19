import { HttpParams } from '@angular/common/http';

export const API = '/api';

/** Construye HttpParams omitiendo vacíos, para que los filtros opcionales no viajen como "null". */
export function params(valores: Record<string, unknown>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(valores)) {
    if (v !== null && v !== undefined && v !== '') {
      p = p.set(k, String(v));
    }
  }
  return p;
}
