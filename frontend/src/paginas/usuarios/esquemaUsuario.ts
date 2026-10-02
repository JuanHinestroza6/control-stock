import { z } from 'zod'

const ROLES = ['ADMIN', 'INVENTARIO', 'VENDEDOR', 'CONTABILIDAD'] as const

const nombre = z.string().trim().min(1, 'Ingresa el nombre').max(100, 'Máximo 100 caracteres')
const email = z.string().trim().min(1, 'Ingresa el correo').max(150, 'Máximo 150 caracteres').email('Correo no válido')
const rol = z.enum(ROLES, { message: 'Selecciona un rol' })

/** Reglas de @PasswordValida del backend (12–72 caracteres, ≤ 72 bytes, no empieza por "cambiar"). */
export const esquemaPasswordTemporal = z
  .string()
  .min(12, 'Mínimo 12 caracteres')
  .max(72, 'Máximo 72 caracteres')
  .refine((v) => new TextEncoder().encode(v).length <= 72, 'Máximo 72 bytes')
  .refine((v) => !v.toLowerCase().startsWith('cambiar'), 'No puede empezar por "cambiar"')

/** Mismo tipo de salida en ambos modos; la contraseña temporal solo se valida al crear. */
export function esquemaUsuario(modo: 'crear' | 'editar') {
  return z
    .object({ nombre, email, rol, passwordTemporal: z.string() })
    .superRefine((datos, ctx) => {
      if (modo !== 'crear') return
      const resultado = esquemaPasswordTemporal.safeParse(datos.passwordTemporal)
      if (!resultado.success) {
        ctx.addIssue({ code: 'custom', path: ['passwordTemporal'], message: resultado.error.issues[0].message })
      }
    })
}

export type DatosFormularioUsuario = z.output<ReturnType<typeof esquemaUsuario>>
