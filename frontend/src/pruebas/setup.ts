import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// findBy*/waitFor esperan hasta 3 s (por defecto 1 s): con la suite completa en un equipo cargado (71 archivos,
// jsdom por archivo) la primera respuesta simulada a veces tardaba más de 1 s y fallaban pruebas distintas en cada corrida.
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
})
