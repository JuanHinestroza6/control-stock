import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useSesion } from './useSesion'

describe('useSesion', () => {
  it('lanza un error claro si se usa fuera de <SesionProvider>', () => {
    const espia = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      expect(() => renderHook(() => useSesion())).toThrow('useSesion debe usarse dentro de <SesionProvider>')
    } finally {
      espia.mockRestore()
    }
  })
})
