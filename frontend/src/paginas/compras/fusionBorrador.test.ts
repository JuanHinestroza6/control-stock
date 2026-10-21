import { describe, expect, it } from 'vitest'
import { fusionar, instantanea, type LineaEditable } from './fusionBorrador'

function linea(varianteId: number, cantidad: string, costo: string): LineaEditable {
  return {
    varianteId,
    sku: `SKU-${varianteId}`,
    descripcion: `Prenda ${varianteId}`,
    cantidad,
    costo,
    sugerida: null,
    justificacion: null,
    inactiva: false,
  }
}

describe('fusionar (three-way por variante)', () => {
  it('sinCambiosDeNadie_noHayConflictosNiCambiosAjenos', () => {
    const l = [linea(1, '10', '5')]
    const r = fusionar(l, l, l, 'obs', 'obs', 'obs')
    expect(r.lineas).toHaveLength(1)
    expect(r.conflictos).toHaveLength(0)
    expect(r.huboCambiosAjenos).toBe(false)
    expect(r.conflictoObservacion).toBeNull()
  })

  it('lineaAgregadaPorAmbosConValoresIguales_noEsConflicto', () => {
    const r = fusionar([], [linea(2, '4', '3')], [linea(2, '4', '3')], '', '', '')
    expect(r.conflictos).toHaveLength(0)
    expect(r.lineas.map((l) => l.varianteId)).toEqual([2])
    expect(r.huboCambiosAjenos).toBe(true)
  })

  it('lineaAgregadaPorAmbosConValoresDistintos_esConflicto', () => {
    const r = fusionar([], [linea(2, '4', '3')], [linea(2, '9', '3')], '', '', '')
    expect(r.lineas).toHaveLength(0)
    expect(r.conflictos).toHaveLength(1)
    expect(r.conflictos[0].mia?.cantidad).toBe('4')
    expect(r.conflictos[0].vigente?.cantidad).toBe('9')
  })

  it('quitadaPorMiYModificadaPorElOtro_esConflictoConMiaNula', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, [], [linea(1, '12', '5')], '', '', '')
    expect(r.conflictos).toHaveLength(1)
    expect(r.conflictos[0].mia).toBeNull()
    expect(r.conflictos[0].vigente?.cantidad).toBe('12')
    expect(r.lineas).toHaveLength(0)
  })

  it('quitadaPorElOtroYModificadaPorMi_esConflictoConVigenteNula', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, [linea(1, '11', '5')], [], '', '', '')
    expect(r.conflictos).toHaveLength(1)
    expect(r.conflictos[0].vigente).toBeNull()
    expect(r.conflictos[0].mia?.cantidad).toBe('11')
  })

  it('quitadaPorAmbos_noApareceNiEsConflicto', () => {
    const r = fusionar([linea(1, '10', '5')], [], [], '', '', '')
    expect(r.lineas).toHaveLength(0)
    expect(r.conflictos).toHaveLength(0)
    expect(r.huboCambiosAjenos).toBe(true)
  })

  it('quitadaSoloPorElOtro_seQuita', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, base, [], '', '', '')
    expect(r.lineas).toHaveLength(0)
    expect(r.conflictos).toHaveLength(0)
  })

  it('quitadaSoloPorMi_seQuita', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, [], base, '', '', '')
    expect(r.lineas).toHaveLength(0)
    expect(r.conflictos).toHaveLength(0)
    expect(r.huboCambiosAjenos).toBe(false)
  })

  it('cambioSoloMio_gana', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, [linea(1, '20', '5')], base, '', '', '')
    expect(r.lineas[0].cantidad).toBe('20')
    expect(r.conflictos).toHaveLength(0)
  })

  it('cambioSoloDelOtro_gana', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, base, [linea(1, '10', '7')], '', '', '')
    expect(r.lineas[0].costo).toBe('7')
    expect(r.conflictos).toHaveLength(0)
    expect(r.huboCambiosAjenos).toBe(true)
  })

  it('costoMioYCantidadDelOtroEnLaMismaLinea_esConflictoDeLineaNoFusionPorCampo', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, [linea(1, '10', '8')], [linea(1, '15', '5')], '', '', '')
    expect(r.conflictos).toHaveLength(1)
    expect(r.lineas).toHaveLength(0)
  })

  it('cambiosEquivalentesConFormatoDistinto_noSonConflicto', () => {
    const base = [linea(1, '10', '5')]
    const r = fusionar(base, [linea(1, ' 12 ', '5,50')], [linea(1, '12', '5.50')], '', '', '')
    expect(r.conflictos).toHaveLength(0)
    expect(r.lineas).toHaveLength(1)
  })

  it('observacionCambiadaIgualPorAmbos_noEsConflicto', () => {
    const r = fusionar([], [], [], 'a', 'nueva', 'nueva ')
    expect(r.conflictoObservacion).toBeNull()
    expect(r.observacion).toBe('nueva')
    expect(r.huboCambiosAjenos).toBe(true)
  })

  it('observacionCambiadaDistintaPorAmbos_esConflicto', () => {
    const r = fusionar([], [], [], 'a', 'mia', 'otra')
    expect(r.conflictoObservacion).toEqual({ mia: 'mia', vigente: 'otra' })
  })

  it('observacionCambiadaSoloPorElOtro_seTomaLaVigente', () => {
    const r = fusionar([], [], [], 'a', 'a', 'otra')
    expect(r.observacion).toBe('otra')
    expect(r.conflictoObservacion).toBeNull()
  })

  it('observacionCambiadaSoloPorMi_seConservaLaMia', () => {
    const r = fusionar([], [], [], 'a', 'mia', 'a')
    expect(r.observacion).toBe('mia')
    expect(r.huboCambiosAjenos).toBe(false)
  })
})

describe('instantanea', () => {
  it('conLineasEnOtroOrdenYEspacios_produceLaMismaHuella', () => {
    const a = instantanea([linea(2, '1', '2,5'), linea(1, '3', '4')], ' obs ')
    const b = instantanea([linea(1, ' 3', '4'), linea(2, '1', '2.5')], 'obs')
    expect(a).toBe(b)
  })
})
