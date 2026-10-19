import { NombreIcono } from '../shared/icono';

export interface ItemMenu {
  ruta: string;
  texto: string;
  icono: NombreIcono;
  /** Frase corta para los accesos directos de la pantalla de inicio. */
  descripcion: string;
  grupo: string;
  permisos?: string[];
  ciudadano?: boolean;
}

/** Menú principal; cada opción se muestra solo con alguno de sus permisos (el backend vuelve a exigirlos). */
export const MENU: ItemMenu[] = [
  { ruta: '/portal/facturas', texto: 'Mis facturas', icono: 'receipt', grupo: 'Mi cuenta', ciudadano: true,
    descripcion: 'Consulte, descargue y pague sus facturas y cuotas de acuerdos.' },
  { ruta: '/portal/contratos', texto: 'Mis contratos', icono: 'file-text', grupo: 'Mi cuenta', ciudadano: true,
    descripcion: 'Sus servicios, predios, medidores y lecturas de consumo.' },
  { ruta: '/portal/pqr', texto: 'Mis PQR', icono: 'message-square-text', grupo: 'Mi cuenta', ciudadano: true,
    descripcion: 'Radique peticiones, quejas y reclamos y siga su respuesta.' },
  { ruta: '/dashboard', texto: 'Dashboard', icono: 'layout-dashboard', grupo: 'General', permisos: ['DASHBOARD_VER'],
    descripcion: 'Indicadores de facturación, recaudo, cartera y PQR.' },
  { ruta: '/usuarios', texto: 'Usuarios', icono: 'users', grupo: 'Atención', permisos: ['USUARIO_CONSULTAR'],
    descripcion: 'Registro y ficha de usuarios, predios y contratos.' },
  { ruta: '/contratos', texto: 'Contratos', icono: 'file-text', grupo: 'Atención', permisos: ['CONTRATO_CONSULTAR'],
    descripcion: 'Contratos de acueducto, alcantarillado y gas.' },
  { ruta: '/contratos/ordenes', texto: 'Órdenes', icono: 'clipboard-list', grupo: 'Atención', permisos: ['ORDEN_GESTIONAR'],
    descripcion: 'Órdenes de suspensión, reconexión e instalación.' },
  { ruta: '/pqr', texto: 'PQR', icono: 'message-square-text', grupo: 'Atención', permisos: ['PQR_CONSULTAR', 'PQR_CONSULTAR_TODAS'],
    descripcion: 'Bandeja de peticiones, quejas, reclamos y recursos.' },
  { ruta: '/facturacion/lecturas', texto: 'Lecturas', icono: 'gauge', grupo: 'Facturación',
    permisos: ['LECTURA_REGISTRAR', 'LECTURA_REVISAR', 'LECTURA_CARGAR_ARCHIVO'],
    descripcion: 'Registro manual, carga de archivos y revisión de lecturas.' },
  { ruta: '/facturacion/facturas', texto: 'Facturas', icono: 'receipt', grupo: 'Facturación', permisos: ['FACTURA_CONSULTAR'],
    descripcion: 'Consulta, PDF, anulación y reexpedición de facturas.' },
  { ruta: '/facturacion/lotes', texto: 'Lotes de facturación', icono: 'layers', grupo: 'Facturación', permisos: ['FACTURA_GENERAR_LOTE'],
    descripcion: 'Generación mensual de facturas por periodo.' },
  { ruta: '/pagos/registrar', texto: 'Registrar pago', icono: 'banknote', grupo: 'Facturación', permisos: ['PAGO_REGISTRAR_PRESENCIAL'],
    descripcion: 'Pagos presenciales en taquilla con comprobante.' },
  { ruta: '/cartera', texto: 'Cartera', icono: 'wallet', grupo: 'Gestión', permisos: ['CARTERA_CONSULTAR'],
    descripcion: 'Usuarios en mora, cobro persuasivo y acuerdos de pago.' },
  { ruta: '/tarifas', texto: 'Tarifas', icono: 'percent', grupo: 'Gestión', permisos: ['TARIFA_GESTIONAR'],
    descripcion: 'Tarifas por servicio y estrato, subsidios y contribuciones.' },
  { ruta: '/admin', texto: 'Administración', icono: 'settings', grupo: 'Gestión',
    permisos: ['FUNCIONARIO_GESTIONAR', 'ROL_GESTIONAR', 'PARAMETRO_GESTIONAR', 'AUDITORIA_CONSULTAR'],
    descripcion: 'Funcionarios, roles, parámetros, festivos y auditoría.' },
];

/** Nombre de rol legible: GESTOR_PQR → Gestor PQR. */
export function nombreRol(rol: string): string {
  return rol
    .split('_')
    .map((p) => (p.length <= 3 ? p : p.charAt(0) + p.slice(1).toLowerCase()))
    .join(' ');
}
