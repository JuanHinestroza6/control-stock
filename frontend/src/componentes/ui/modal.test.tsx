import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DialogoConfirmacion } from './DialogoConfirmacion'
import { Modal } from './Modal'

describe('Modal', () => {
  it('cerrado no renderiza nada', () => {
    render(<Modal abierto={false} titulo="T" onCerrar={vi.fn()}>x</Modal>)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('abierto expone dialog con nombre accesible y enfoca el primer control', () => {
    render(
      <Modal abierto titulo="Editar" onCerrar={vi.fn()}>
        <button>Uno</button>
      </Modal>,
    )
    expect(screen.getByRole('dialog', { name: 'Editar' })).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByRole('button', { name: 'Uno' })).toHaveFocus()
  })

  it('Escape y clic en el fondo cierran; clic dentro no', async () => {
    const onCerrar = vi.fn()
    render(
      <Modal abierto titulo="T" onCerrar={onCerrar}>
        <button>Uno</button>
      </Modal>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Uno' }))
    expect(onCerrar).not.toHaveBeenCalled()
    await userEvent.keyboard('{Escape}')
    expect(onCerrar).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('dialog').parentElement as HTMLElement)
    expect(onCerrar).toHaveBeenCalledTimes(2)
  })

  it('bloqueado ignora Escape y fondo', async () => {
    const onCerrar = vi.fn()
    render(
      <Modal abierto bloqueado titulo="T" onCerrar={onCerrar}>
        <button>Uno</button>
      </Modal>,
    )
    await userEvent.keyboard('{Escape}')
    await userEvent.click(screen.getByRole('dialog').parentElement as HTMLElement)
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('atrapa el foco con Tab y Shift+Tab', async () => {
    render(
      <Modal abierto titulo="T" onCerrar={vi.fn()}>
        <button>Uno</button>
        <button>Dos</button>
      </Modal>,
    )
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Dos' })).toHaveFocus()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Uno' })).toHaveFocus()
    await userEvent.tab({ shift: true })
    expect(screen.getByRole('button', { name: 'Dos' })).toHaveFocus()
  })

  it('al cerrar devuelve el foco al disparador y restaura el scroll', () => {
    const { rerender } = render(
      <>
        <button>Abrir</button>
        <Modal abierto={false} titulo="T" onCerrar={vi.fn()}>
          <button>Uno</button>
        </Modal>
      </>,
    )
    const abrir = screen.getByRole('button', { name: 'Abrir' })
    abrir.focus()
    const modal = (abierto: boolean) => (
      <>
        <button>Abrir</button>
        <Modal abierto={abierto} titulo="T" onCerrar={vi.fn()}>
          <button>Uno</button>
        </Modal>
      </>
    )
    rerender(modal(true))
    expect(document.body.style.overflow).toBe('hidden')
    rerender(modal(false))
    expect(document.body.style.overflow).toBe('')
    expect(abrir).toHaveFocus()
  })
})

describe('DialogoConfirmacion', () => {
  it('confirma y cancela', async () => {
    const onConfirmar = vi.fn()
    const onCancelar = vi.fn()
    render(
      <DialogoConfirmacion abierto titulo="Borrar" mensaje="¿Seguro?" textoConfirmar="Borrar" peligro onConfirmar={onConfirmar} onCancelar={onCancelar} />,
    )
    expect(screen.getByText('¿Seguro?')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Borrar' })).toHaveClass('bg-critico')
    await userEvent.click(screen.getByRole('button', { name: 'Borrar' }))
    expect(onConfirmar).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCancelar).toHaveBeenCalledTimes(1)
  })

  it('muestra el error del backend y, cargando, bloquea cancelar y Escape', async () => {
    const onCancelar = vi.fn()
    render(
      <DialogoConfirmacion abierto cargando error="Hay reservas activas" titulo="T" mensaje="m" onConfirmar={vi.fn()} onCancelar={onCancelar} />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Hay reservas activas')
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    expect(onCancelar).not.toHaveBeenCalled()
  })
})
