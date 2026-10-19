import { FormControl } from '@angular/forms';

import { validadorParametro } from './parametros';

function valido(tipo: Parameters<typeof validadorParametro>[0], clave: string, valor: string): boolean {
  return new FormControl(valor, validadorParametro(tipo, clave)).valid;
}

describe('validadorParametro', () => {
  it('replica el formato de CK_PARAMETRO_*', () => {
    expect(valido('ENTERO', 'X', '24')).toBe(true);
    expect(valido('ENTERO', 'X', '2.5')).toBe(false);
    expect(valido('DECIMAL', 'X', '25.37')).toBe(true);
    expect(valido('DECIMAL', 'X', '25,37')).toBe(false);
    expect(valido('BOOLEANO', 'X', 'S')).toBe(true);
    expect(valido('BOOLEANO', 'X', 'SI')).toBe(false);
  });

  it('restringe MODO_OPERACION a los tres modos', () => {
    expect(valido('TEXTO', 'MODO_OPERACION', 'CONTINGENCIA')).toBe(true);
    expect(valido('TEXTO', 'MODO_OPERACION', 'APAGADO')).toBe(false);
    expect(valido('TEXTO', 'OTRO', '<b>x</b>')).toBe(false);
  });
});
