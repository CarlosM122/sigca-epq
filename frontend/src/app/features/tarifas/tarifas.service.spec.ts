import { describe, expect, it } from 'vitest';

import { topesFactor } from './tarifas.service';

describe('topesFactor', () => {
  it('aplica los topes legales de subsidio por estrato', () => {
    expect(topesFactor('RESIDENCIAL', 1)).toEqual([-70, 0]);
    expect(topesFactor('RESIDENCIAL', 3)).toEqual([-15, 0]);
    expect(topesFactor('RESIDENCIAL', 4)).toEqual([0, 0]);
    expect(topesFactor('RESIDENCIAL', 6)).toEqual([0, 100]);
  });

  it('el uso oficial no tiene factor y el comercial solo contribución', () => {
    expect(topesFactor('OFICIAL', null)).toEqual([0, 0]);
    expect(topesFactor('COMERCIAL', null)).toEqual([0, 100]);
  });
});
