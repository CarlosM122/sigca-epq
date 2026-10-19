import { FormControl, FormGroup } from '@angular/forms';

import { correo, digitoVerificacionNit, documentoValido, identificacionValida, passwordSegura, sinHtml, telefonoColombia } from './validadores';

describe('validadores', () => {
  it('calcula el dígito de verificación del NIT como la DIAN', () => {
    expect(digitoVerificacionNit('890000464')).toBe(3);
    expect(digitoVerificacionNit('800197268')).toBe(4); // DIAN
    expect(identificacionValida('NIT', '890000464-3')).toBe(true);
    expect(identificacionValida('NIT', '890000464-4')).toBe(false);
  });

  it('valida el formato de cada tipo de documento', () => {
    expect(identificacionValida('CC', '1094123456')).toBe(true);
    expect(identificacionValida('CC', '0123456')).toBe(false);
    expect(identificacionValida('TI', '123456789')).toBe(false);
    expect(identificacionValida('PA', 'AB12345')).toBe(true);
  });

  it('exige NIT para persona jurídica', () => {
    const g = new FormGroup(
      {
        tipoPersona: new FormControl('JURIDICA'),
        tipoIdentificacion: new FormControl('CC'),
        numeroIdentificacion: new FormControl('1094123456'),
      },
      { validators: documentoValido() },
    );
    expect(g.get('numeroIdentificacion')!.hasError('documentoPersona')).toBe(true);
  });

  it('exige contraseña robusta', () => {
    expect(passwordSegura(new FormControl('corta'))).not.toBeNull();
    expect(passwordSegura(new FormControl('Segura.2026'))).toBeNull();
  });

  it('valida teléfono, correo y HTML', () => {
    expect(telefonoColombia(new FormControl('3001234567'))).toBeNull();
    expect(telefonoColombia(new FormControl('+576067412345'))).toBeNull();
    expect(telefonoColombia(new FormControl('2001234567'))).not.toBeNull();
    expect(correo(new FormControl('ana@epq.gov.co'))).toBeNull();
    expect(correo(new FormControl('ana@'))).not.toBeNull();
    expect(sinHtml(new FormControl('<script>x</script>'))).not.toBeNull();
  });
});
