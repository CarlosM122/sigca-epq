import { Routes } from '@angular/router';

/** Tarifas CRA/CREG y factores de subsidio y contribución (P-08). El guarda de permiso va en app.routes.ts. */
export const TARIFAS_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./tarifas').then((m) => m.Tarifas) },
];
