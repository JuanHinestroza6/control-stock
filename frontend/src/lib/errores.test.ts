import { AxiosError, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { MENSAJE_POR_DEFECTO, MENSAJE_SIN_CONEXION, erroresPorCampo, mensajeError } from './errores'
import type { ProblemDetail } from '../tipos/problema'

function errorAxios(status: number, data: ProblemDetail | string): AxiosError {
  const respuesta = { status, data, statusText: '', headers: {}, config: {} } as AxiosResponse
  return new AxiosError('fallo', 'ERR_BAD_REQUEST', undefined, undefined, respuesta)
}

describe('mensajeError', () => {
  it('usa detail del ProblemDetail', () => {
    expect(mensajeError(errorAxios(401, { detail: 'Credenciales inválidas', title: 'No autorizado' }))).toBe(
      'Credenciales inválidas',
    )
  })

  it('usa title si no hay detail', () => {
    expect(mensajeError(errorAxios(409, { title: 'Conflicto' }))).toBe('Conflicto')
  })

  it('prioriza los errores por campo', () => {
    const error = errorAxios(400, {
      detail: 'Solicitud inválida',
      errores: [
        { campo: 'email', mensaje: 'no es válido' },
        { campo: 'password', mensaje: 'es obligatoria' },
      ],
    })
    expect(mensajeError(error)).toBe('email: no es válido. password: es obligatoria')
    expect(erroresPorCampo(error)).toEqual({ email: 'no es válido', password: 'es obligatoria' })
  })

  it('mensaje por defecto con cuerpo no ProblemDetail', () => {
    expect(mensajeError(errorAxios(500, 'texto plano'))).toBe(MENSAJE_POR_DEFECTO)
    expect(mensajeError(errorAxios(500, {}))).toBe(MENSAJE_POR_DEFECTO)
  })

  it('mensaje de conexión si no hubo respuesta', () => {
    expect(mensajeError(new AxiosError('Network Error', 'ERR_NETWORK'))).toBe(MENSAJE_SIN_CONEXION)
  })

  it('Error genérico y valores desconocidos', () => {
    expect(mensajeError(new Error('boom'))).toBe(MENSAJE_POR_DEFECTO)
    expect(mensajeError(undefined)).toBe(MENSAJE_POR_DEFECTO)
    expect(erroresPorCampo(new Error('x'))).toEqual({})
  })

  it('no lanza si errores no es un arreglo o trae elementos inválidos', () => {
    const raro = errorAxios(400, { detail: 'Solicitud inválida', errores: 'mal' } as unknown as ProblemDetail)
    expect(mensajeError(raro)).toBe('Solicitud inválida')
    expect(erroresPorCampo(raro)).toEqual({})
    const mezcla = errorAxios(400, { errores: [null, 5, { campo: 'x', mensaje: 'y' }] } as unknown as ProblemDetail)
    expect(erroresPorCampo(mezcla)).toEqual({ x: 'y' })
  })

  it('cuerpo arreglo no es ProblemDetail', () => {
    expect(mensajeError(errorAxios(500, [] as unknown as ProblemDetail))).toBe(MENSAJE_POR_DEFECTO)
  })
})
