import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { EstadoCarga, EstadoError, EstadoVacio } from './Estados'
import { rangoVisible } from '../../lib/paginacion'
import { Paginacion } from './Paginacion'
import { Selector } from './Selector'
import { Tabla, type Columna } from './Tabla'

describe('rangoVisible', () => {
  it('calcula el rango de la página y recorta en la última', () => {
    expect(rangoVisible({ size: 20, number: 0, totalElements: 45, totalPages: 3 })).toEqual({ desde: 1, hasta: 20 })
    expect(rangoVisible({ size: 20, number: 2, totalElements: 45, totalPages: 3 })).toEqual({ desde: 41, hasta: 45 })
  })

  it('sin elementos devuelve 0-0', () => {
    expect(rangoVisible({ size: 20, number: 0, totalElements: 0, totalPages: 0 })).toEqual({ desde: 0, hasta: 0 })
  })
})

describe('Paginacion', () => {
  it('en la primera página deshabilita Anterior y avanza con Siguiente', async () => {
    const onCambiar = vi.fn()
    render(<Paginacion pagina={{ size: 20, number: 0, totalElements: 45, totalPages: 3 }} onCambiar={onCambiar} />)
    expect(screen.getByText('1–20 de 45')).toBeInTheDocument()
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(onCambiar).toHaveBeenCalledWith(1)
  })

  it('en la última página deshabilita Siguiente y retrocede con Anterior', async () => {
    const onCambiar = vi.fn()
    render(<Paginacion pagina={{ size: 20, number: 2, totalElements: 45, totalPages: 3 }} onCambiar={onCambiar} />)
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Anterior' }))
    expect(onCambiar).toHaveBeenCalledWith(1)
  })

  it('sin resultados muestra "Página 1 de 1" y ambos botones deshabilitados', () => {
    render(<Paginacion pagina={{ size: 20, number: 0, totalElements: 0, totalPages: 0 }} onCambiar={vi.fn()} />)
    expect(screen.getByText('Página 1 de 1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
  })
})

describe('Tabla', () => {
  interface Fila {
    id: number
    nombre: string
  }
  const columnas: Columna<Fila>[] = [
    { clave: 'id', encabezado: 'Id', celda: (f) => f.id },
    { clave: 'nombre', encabezado: 'Nombre', celda: (f) => f.nombre, ocultarEnMovil: true },
  ]

  it('renderiza encabezados y una fila por elemento', () => {
    render(
      <Tabla
        columnas={columnas}
        filas={[
          { id: 1, nombre: 'Ana' },
          { id: 2, nombre: 'Luis' },
        ]}
        claveFila={(f) => f.id}
        descripcion="Personas"
      />,
    )
    const tabla = screen.getByRole('table', { name: 'Personas' })
    expect(within(tabla).getAllByRole('columnheader')).toHaveLength(2)
    expect(within(tabla).getAllByRole('row')).toHaveLength(3)
    expect(within(tabla).getByText('Luis')).toBeInTheDocument()
  })
})

describe('Selector', () => {
  it('ofrece el placeholder como valor vacío y notifica el cambio', async () => {
    const onChange = vi.fn()
    render(
      <Selector
        etiqueta="Categoría"
        placeholder="Todas"
        opciones={[{ valor: '1', etiqueta: 'Camisetas' }]}
        onChange={(e) => onChange(e.target.value)}
      />,
    )
    const select = screen.getByLabelText('Categoría')
    expect(select).toHaveValue('')
    await userEvent.selectOptions(select, 'Camisetas')
    expect(onChange).toHaveBeenCalledWith('1')
  })
})

describe('Estados', () => {
  it('error: muestra el mensaje y reintenta', async () => {
    const onReintentar = vi.fn()
    render(<EstadoError mensaje="Falló" onReintentar={onReintentar} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Falló')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onReintentar).toHaveBeenCalledTimes(1)
  })

  it('error sin onReintentar no muestra botón', () => {
    render(<EstadoError mensaje="Falló" />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('vacío muestra mensaje y acción; carga expone región de estado', () => {
    render(
      <>
        <EstadoVacio mensaje="Sin datos" accion={<button>Crear</button>} />
        <EstadoCarga filas={2} />
      </>,
    )
    expect(screen.getByText('Sin datos')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
  })
})
