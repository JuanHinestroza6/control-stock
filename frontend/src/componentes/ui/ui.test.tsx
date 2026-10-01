import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Boton } from './Boton'
import { Campo } from './Campo'
import { Tarjeta } from './Tarjeta'

describe('Boton', () => {
  it('dispara onClick', async () => {
    const onClick = vi.fn()
    render(<Boton onClick={onClick}>Guardar</Boton>)
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('en estado cargando se deshabilita y no dispara onClick', async () => {
    const onClick = vi.fn()
    render(
      <Boton cargando onClick={onClick}>
        Guardar
      </Boton>,
    )
    const boton = screen.getByRole('button', { name: 'Guardar' })
    expect(boton).toBeDisabled()
    expect(boton).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(boton)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('aplica la variante peligro', () => {
    render(<Boton variante="peligro">Borrar</Boton>)
    expect(screen.getByRole('button', { name: 'Borrar' })).toHaveClass('bg-critico')
  })
})

describe('Campo', () => {
  it('asocia la etiqueta con el input', () => {
    render(<Campo etiqueta="Correo" />)
    expect(screen.getByLabelText('Correo')).toBeInTheDocument()
  })

  it('muestra el error y lo enlaza de forma accesible', () => {
    render(<Campo etiqueta="Correo" error="Es obligatorio" />)
    const input = screen.getByLabelText('Correo')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Es obligatorio')
    expect(screen.getByRole('alert')).toHaveTextContent('Es obligatorio')
  })

  it('reenvía la ref al input', () => {
    const ref = { current: null as HTMLInputElement | null }
    render(<Campo etiqueta="Correo" ref={ref} />)
    expect(ref.current).toBeInstanceOf(HTMLInputElement)
  })
})

describe('Tarjeta', () => {
  it('renderiza su contenido', () => {
    render(<Tarjeta>Hola</Tarjeta>)
    expect(screen.getByText('Hola')).toHaveClass('rounded-lg')
  })
})
