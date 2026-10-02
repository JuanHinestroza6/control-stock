import { z } from 'zod'

/** Reglas de @PasswordValida del backend (el backend sigue siendo la fuente de verdad). */
export const esquemaCambioPassword = z
  .object({
    actual: z.string().min(1, 'Ingresa tu contraseña actual').max(72, 'Máximo 72 caracteres'),
    nueva: z
      .string()
      .min(12, 'Mínimo 12 caracteres')
      .max(72, 'Máximo 72 caracteres')
      .refine((v) => new TextEncoder().encode(v).length <= 72, 'Máximo 72 bytes')
      .refine((v) => !v.toLowerCase().startsWith('cambiar'), 'No puede empezar por "cambiar"'),
    confirmacion: z.string(),
  })
  .refine((d) => d.nueva === d.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden' })
  .refine((d) => d.nueva !== d.actual, { path: ['nueva'], message: 'Debe ser distinta de la actual' })

export type FormularioCambioPasswordDatos = z.infer<typeof esquemaCambioPassword>
