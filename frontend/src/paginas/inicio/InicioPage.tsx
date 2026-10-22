import { lazy, Suspense, type ReactNode } from 'react'
import { Link } from 'react-router'
import { TarjetaIndicador } from '../../componentes/dominio/TarjetaIndicador'
import { modulosPermitidos, puedeVerAlertas, rutaPermitida } from '../../componentes/layout/modulos'
import { EstadoCarga, EstadoError } from '../../componentes/ui/Estados'
import { Tarjeta } from '../../componentes/ui/Tarjeta'
import { usePanel } from '../../hooks/usePanel'
import { useSesion } from '../../hooks/useSesion'
import { mensajeError } from '../../lib/errores'
import { formatearMoneda } from '../../lib/formato'
import { puedeVerAnaliticaCompleta } from '../../lib/permisos'
import { etiquetaRol } from '../../lib/roles'
import type { Rol } from '../../tipos/auth'
import type { PanelResponse } from '../../tipos/panel'
import { BloqueAlertas, BloqueEjecucion, BloqueMasVendidos } from './BloquesInicio'

const GraficoVentas7Dias = lazy(() => import('../../componentes/graficos/GraficoVentas7Dias'))

const num = (n: number) => n.toLocaleString('es-CO')
const pct = (n: number) => `${n.toLocaleString('es-CO', { maximumFractionDigits: 1 })} %`
const horaGenerado = (iso: string) =>
  new Intl.DateTimeFormat('es-CO', { timeZone: 'America/Bogota', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(iso))

function Seccion({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="mt-6" aria-label={titulo}>
      <h2 className="mb-2 text-lg">{titulo}</h2>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{children}</div>
    </section>
  )
}

function Indicadores({ panel, rol }: { panel: PanelResponse; rol: Rol }) {
  const enlace = (ruta: string) => (rutaPermitida(ruta, rol) ? ruta : undefined)
  const { ventas, inventario, reservas, compras } = panel
  return (
    <>
      {ventas && (
        <>
          <Seccion titulo="Ventas">
            <TarjetaIndicador titulo="Ventas de hoy" valor={formatearMoneda(ventas.ventasHoy)} detalle={`${num(ventas.numeroVentasHoy)} ventas`} a={enlace('/ventas')} />
            <TarjetaIndicador titulo="Ventas del mes" valor={formatearMoneda(ventas.ventasMes)} detalle={`${num(ventas.numeroVentasMes)} ventas`} a={enlace('/ventas')} />
            {ventas.margenMes != null && (
              <TarjetaIndicador
                titulo="Margen del mes"
                valor={formatearMoneda(ventas.margenMes)}
                detalle={ventas.margenMesPorcentaje != null ? pct(ventas.margenMesPorcentaje) : undefined}
                tono={ventas.margenMes < 0 ? 'critico' : 'ok'}
                a={enlace('/reportes')}
              />
            )}
          </Seccion>
          <Tarjeta className="mt-4">
            <h2 className="mb-2 text-base">Ventas de los últimos 7 días</h2>
            <Suspense fallback={<EstadoCarga filas={3} />}>
              <GraficoVentas7Dias
                puntos={ventas.ultimos7Dias}
                formatoValor={formatearMoneda}
                descripcion={`Gráfico de barras: ingreso neto de los últimos 7 días. ${ventas.ultimos7Dias.map((p) => `${p.fecha}: ${formatearMoneda(p.total)}`).join('; ')}.`}
              />
            </Suspense>
          </Tarjeta>
        </>
      )}
      {inventario && (
        <Seccion titulo="Inventario">
          <TarjetaIndicador
            titulo="Variantes activas"
            valor={num(inventario.variantesActivas)}
            detalle={`${num(inventario.unidadesFisicas)} u físicas · ${num(inventario.unidadesDisponibles)} disponibles`}
            a={enlace('/inventario')}
          />
          <TarjetaIndicador titulo="Agotadas" valor={num(inventario.alertasAgotado)} tono={inventario.alertasAgotado > 0 ? 'critico' : 'ok'} a={enlace('/alertas')} />
          <TarjetaIndicador titulo="Bajo stock" valor={num(inventario.alertasStockBajo)} tono={inventario.alertasStockBajo > 0 ? 'alerta' : 'ok'} a={enlace('/alertas')} />
          <TarjetaIndicador titulo="Sin rotación" valor={num(inventario.alertasSinRotacion)} a={enlace('/alertas')} />
          {inventario.valorCosto != null && <TarjetaIndicador titulo="Valor a costo" valor={formatearMoneda(inventario.valorCosto)} />}
          {inventario.valorVenta != null && <TarjetaIndicador titulo="Valor a precio de venta" valor={formatearMoneda(inventario.valorVenta)} />}
        </Seccion>
      )}
      {reservas && (
        <Seccion titulo="Apartados">
          <TarjetaIndicador titulo="Apartados activos" valor={num(reservas.activas)} detalle={`${num(reservas.unidadesReservadas)} unidades reservadas`} a={enlace('/reservas')} />
          <TarjetaIndicador titulo="Por vencer en 24 h" valor={num(reservas.porVencer24h)} tono={reservas.porVencer24h > 0 ? 'alerta' : 'ok'} a={enlace('/reservas')} />
        </Seccion>
      )}
      {compras && (
        <Seccion titulo="Compras">
          <TarjetaIndicador titulo="Planes en borrador" valor={num(compras.planesBorrador)} a={enlace('/compras')} />
          <TarjetaIndicador
            titulo="Planes en camino"
            valor={num(compras.planesEnCamino)}
            detalle={`${num(compras.unidadesEnCamino)} unidades${compras.valorEnCamino != null ? ` · ${formatearMoneda(compras.valorEnCamino)}` : ''}`}
            a={enlace('/compras')}
          />
          <TarjetaIndicador titulo="Planes atrasados" valor={num(compras.planesAtrasados)} tono={compras.planesAtrasados > 0 ? 'critico' : 'ok'} a={enlace('/compras')} />
        </Seccion>
      )}
    </>
  )
}

export function InicioPage() {
  const { usuario } = useSesion()
  const panel = usePanel()
  if (!usuario) return null
  const rol = usuario.rol
  const accesos = modulosPermitidos(rol).filter((m) => m.ruta !== '/')

  return (
    <section>
      <h1>Hola, {usuario.nombre}</h1>
      <p className="mt-1 text-oxford/70">
        Bienvenido a Control Stock. Tu rol es <strong>{etiquetaRol(rol)}</strong>.
      </p>

      <div className="mt-4">
        {panel.isPending ? (
          <EstadoCarga filas={4} />
        ) : panel.isError ? (
          <EstadoError mensaje={mensajeError(panel.error)} onReintentar={() => void panel.refetch()} />
        ) : (
          <>
            <p className="text-xs text-oxford/60">Generado a las {horaGenerado(panel.data.generadoEn)}</p>
            <Indicadores panel={panel.data} rol={rol} />
          </>
        )}
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        {puedeVerAlertas(rol) && <BloqueAlertas />}
        <BloqueMasVendidos />
        {puedeVerAnaliticaCompleta(rol) && <BloqueEjecucion />}
      </div>

      <h2 className="mt-8 text-lg">Accesos</h2>
      <ul className="mt-2 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {accesos.map((m) => (
          <li key={m.clave}>
            <Link to={m.ruta} className="block rounded-lg focus-visible:outline-2 focus-visible:outline-acento">
              <Tarjeta className="h-full transition-shadow hover:shadow-md">
                <h2 className="text-acento">{m.etiqueta}</h2>
                <p className="mt-1 text-oxford/70">{m.descripcion}</p>
              </Tarjeta>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
