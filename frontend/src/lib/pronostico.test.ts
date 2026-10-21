import { describe, expect, it } from 'vitest'
import { etiquetaMes, porcentaje, textoMotivo, tonoWape } from './pronostico'

describe('tonoWape', () => {
  it('null es neutro', () => {
    expect(tonoWape(null)).toBe('neutro')
  })

  it('0,20 exacto es ok y justo encima es alerta', () => {
    expect(tonoWape(0.2)).toBe('ok')
    expect(tonoWape(0.2001)).toBe('alerta')
  })

  it('0,40 exacto es alerta y justo encima es critico', () => {
    expect(tonoWape(0.4)).toBe('alerta')
    expect(tonoWape(0.4001)).toBe('critico')
  })

  it('0 es ok', () => {
    expect(tonoWape(0)).toBe('ok')
  })
})

describe('porcentaje', () => {
  it('formatea con coma decimal y un decimal', () => {
    expect(porcentaje(0.153)).toBe('15,3 %')
    expect(porcentaje(0)).toBe('0,0 %')
    expect(porcentaje(1)).toBe('100,0 %')
  })
})

describe('etiquetaMes', () => {
  it('convierte YYYY-MM en mes abreviado y año', () => {
    expect(etiquetaMes('2026-03')).toBe('mar 2026')
    expect(etiquetaMes('2025-12')).toBe('dic 2025')
  })
})

describe('textoMotivo', () => {
  it('método null avisa de falta de historia', () => {
    expect(textoMotivo({ metodo: null, motivoSinHw: null })).toMatch(/Sin historia/)
  })

  it('sin motivo devuelve null', () => {
    expect(textoMotivo({ metodo: 'SES', motivoSinHw: null })).toBeNull()
  })

  it('con motivo explica por qué no compitió Holt-Winters', () => {
    expect(textoMotivo({ metodo: 'SES', motivoSinHw: 'MES_EN_CERO' })).toBe(
      'Sin Holt-Winters: tiene meses sin demanda',
    )
  })
})
