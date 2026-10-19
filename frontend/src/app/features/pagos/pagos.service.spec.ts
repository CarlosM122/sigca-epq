import { PATRON_REFERENCIA, urlCheckoutSegura } from './pagos.service';

describe('Pago en línea', () => {
  it('solo sigue URLs https o rutas propias para el checkout', () => {
    expect(urlCheckoutSegura('https://pasarela.example.com/pagar/TX-1')).toBe(true);
    expect(urlCheckoutSegura('/api/publico/pasarela-simulada/TX-ABC')).toBe(true);
    expect(urlCheckoutSegura('//evil.example.com/x')).toBe(false);
    expect(urlCheckoutSegura('http://pasarela.example.com')).toBe(false);
    expect(urlCheckoutSegura('javascript:alert(1)')).toBe(false);
  });

  it('acepta referencias de factura y de acuerdo con o sin cuota', () => {
    expect(PATRON_REFERENCIA.test('FAC-202608-0000123')).toBe(true);
    expect(PATRON_REFERENCIA.test('AP-2026-000045')).toBe(true);
    expect(PATRON_REFERENCIA.test('AP-2026-000045-03')).toBe(true);
    expect(PATRON_REFERENCIA.test('AP-2026-000045-3')).toBe(false);
    expect(PATRON_REFERENCIA.test('FAC-2026-1')).toBe(false);
  });
});
