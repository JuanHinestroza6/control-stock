package co.livorno.controlstock.semilla;

import co.livorno.controlstock.catalogo.modelo.Talla;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Modelo de demanda de los datos semilla (docs/especificaciones/datos-semilla.md, plan de la Fase 4). Puro: sin Spring
 * ni base de datos; toda la aleatoriedad sale del {@link Random} inyectado (semilla 42 en el generador), así la misma semilla da
 * la misma secuencia. Solo recorre listas y arreglos en orden fijo.
 *
 * <pre>
 * tickets(día) ~ Poisson(λ)
 * λ(día)       = ticketsDiaBase × factorMes(mes) × factorDía(díaSemana) × 1,015^mesesDesdeInicio
 * líneas por ticket: 1 (70 %), 2 (22 %), 3 (8 %), productos distintos elegidos con pesos escalonados
 * peso(producto) = pesosEscalonados(n)[rango − 1]   (estancados = 0 en los últimos 90 días, el resto se renormaliza)
 * talla: S 20 %, M 35 %, L 30 %, XL 15 % (accesorios: UNICA)
 * color: el primero 50 %, el resto se reparte por igual
 * cantidad: 1 (85 %) o 2 (15 %); descuento: 15 % de las líneas, 5–20 %; hora 09:00–21:00
 * </pre>
 */
public final class ModeloDemanda {

    /**
     * Participación teórica de la cabeza (el 20 % de los rangos: 6 de 30 productos). Calibrada para que en la simulación
     * de 24 meses el 20 % superior concentre ≈ 78 % de las unidades (ver {@link #pesosEscalonados}).
     */
    public static final double CUOTA_CABEZA = 0.78;
    /** Participación del grupo medio (el 20 % siguiente de los rangos). */
    public static final double CUOTA_MEDIO = 0.12;
    /** Participación de la cola (el 60 % restante de los rangos). */
    public static final double CUOTA_COLA = 0.10;
    /** Perfil dentro de la cabeza: lineal del primero (1,6) al último (1,0); 16 % … 10 % con 6 productos. */
    private static final double PERFIL_CABEZA_PRIMERO = 1.6;
    private static final double PERFIL_CABEZA_ULTIMO = 1.0;
    private static final double FRACCION_GRUPO = 0.2;

    /** Estacionalidad colombiana de moda, enero..diciembre (docs/especificaciones/datos-semilla.md). */
    private static final double[] FACTOR_MES = {0.8, 0.7, 0.8, 0.85, 1.0, 1.25, 1.0, 0.9, 0.95, 1.0, 1.3, 1.6};

    /** Crecimiento mensual de la empresa: tendencia = 1,015^mes. */
    public static final double TENDENCIA_MENSUAL = 1.015;

    /** Días finales de la historia (contando {@code fin}) en los que los productos estancados no venden nada. */
    public static final int DIAS_ESTANCAMIENTO = 90;

    /** Días mínimos de demanda normal al inicio de la historia, para que los estancados tengan stock y ventas previas. */
    public static final int DIAS_PREVIOS_AL_ESTANCAMIENTO = 30;

    private static final double[] PROBABILIDAD_LINEAS = {0.70, 0.22, 0.08};
    /** E[líneas por ticket] = 1×0,70 + 2×0,22 + 3×0,08 = 1,38. */
    public static final double LINEAS_ESPERADAS = 1.38;

    private static final double PROBABILIDAD_CANTIDAD_DOS = 0.15;
    /** E[cantidad por línea] = 1×0,85 + 2×0,15 = 1,15. */
    public static final double CANTIDAD_ESPERADA = 1.15;

    private static final double PROBABILIDAD_DESCUENTO = 0.15;
    private static final int DESCUENTO_MINIMO = 5;
    private static final int DESCUENTO_MAXIMO = 20;

    private static final LocalTime APERTURA = LocalTime.of(9, 0);
    private static final int MINUTOS_ABIERTO = 12 * 60;  // 09:00–21:00

    private static final double PARTICIPACION_PRIMER_COLOR = 0.5;

    /** Por encima de este λ, Knuth sumaría demasiados factores: se suma Poisson(30) por tramos (es exacto). */
    private static final double LAMBDA_MAXIMO_KNUTH = 30.0;

    private final List<ProductoSemilla> productos;
    private final double[] pesosBase;
    private final LocalDate inicio;
    private final LocalDate inicioEstancamiento;
    private final double ticketsDiaBase;
    private final Random random;

    /**
     * @param productos      catálogo (orden fijo); los rangos de demanda deben ser 1..n sin repetir
     * @param inicio         primer día de la historia (mes 0 de la tendencia)
     * @param fin            último día de la historia (los estancados no venden en sus últimos 90 días)
     * @param ticketsDiaBase tickets esperados en un día con todos los factores en 1
     * @param random         fuente de aleatoriedad (semilla fija para reproducir la carga)
     */
    public ModeloDemanda(List<ProductoSemilla> productos, LocalDate inicio, LocalDate fin, double ticketsDiaBase,
                         Random random) {
        this.productos = List.copyOf(productos);
        this.inicio = Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        this.random = Objects.requireNonNull(random, "random");
        if (this.productos.isEmpty()) {
            throw new IllegalArgumentException("El catálogo no puede estar vacío");
        }
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }
        if (!(ticketsDiaBase > 0) || Double.isInfinite(ticketsDiaBase)) {
            throw new IllegalArgumentException("ticketsDiaBase debe ser positivo");
        }
        this.ticketsDiaBase = ticketsDiaBase;
        LocalDate desdeEstancamiento = fin.minusDays(DIAS_ESTANCAMIENTO - 1L);
        LocalDate primerPosible = inicio.plusDays(DIAS_PREVIOS_AL_ESTANCAMIENTO);  // historias cortas
        this.inicioEstancamiento = desdeEstancamiento.isBefore(primerPosible) ? primerPosible : desdeEstancamiento;

        int n = this.productos.size();
        double[] escalonados = pesosEscalonados(n);
        boolean[] rangoUsado = new boolean[n + 1];
        this.pesosBase = new double[n];
        for (int i = 0; i < n; i++) {
            int rango = this.productos.get(i).rangoDemanda();
            if (rango > n || rangoUsado[rango]) {
                throw new IllegalArgumentException("Los rangos de demanda deben ser 1.." + n + " sin repetir");
            }
            rangoUsado[rango] = true;
            pesosBase[i] = escalonados[rango - 1];
        }
    }

    // ------------------------------------------------------------------ funciones puras

    /** Factor de estacionalidad del mes: dic 1,6 (prima y navidad) … feb 0,7. */
    public static double factorMes(Month mes) {
        return FACTOR_MES[mes.getValue() - 1];
    }

    /** lun–jue 0,85; vie 1,1; sáb 1,3; dom 1,0. */
    public static double factorDiaSemana(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY -> 0.85;
            case FRIDAY -> 1.1;
            case SATURDAY -> 1.3;
            case SUNDAY -> 1.0;
        };
    }

    /** {@code 1,015^meses}. Ejemplo: 12 meses → 1,015¹² ≈ 1,1956. */
    public static double tendencia(int mesesDesdeInicio) {
        if (mesesDesdeInicio < 0) {
            throw new IllegalArgumentException("Los meses desde el inicio no pueden ser negativos");
        }
        return Math.pow(TENDENCIA_MENSUAL, mesesDesdeInicio);
    }

    /**
     * Muestra de una Poisson(λ) implementada a mano (algoritmo de Knuth): multiplica uniformes hasta que el producto
     * cae bajo e^(−λ); el número de factores menos uno es la muestra. Para λ > 30 suma tramos de Poisson(30), porque la
     * suma de Poisson independientes es Poisson con la suma de las λ (y e^(−λ) grande se volvería 0 en double).
     * λ = 0 devuelve 0 sin consumir aleatorios.
     */
    public static int poisson(double lambda, Random random) {
        if (!(lambda >= 0) || Double.isInfinite(lambda)) {
            throw new IllegalArgumentException("λ debe ser un número finito mayor o igual a 0: " + lambda);
        }
        int total = 0;
        double restante = lambda;
        while (restante > LAMBDA_MAXIMO_KNUTH) {
            total += poissonKnuth(LAMBDA_MAXIMO_KNUTH, random);
            restante -= LAMBDA_MAXIMO_KNUTH;
        }
        return restante == 0 ? total : total + poissonKnuth(restante, random);
    }

    private static int poissonKnuth(double lambda, Random random) {
        double limite = Math.exp(-lambda);
        double producto = random.nextDouble();
        int k = 0;
        while (producto > limite) {
            k++;
            producto *= random.nextDouble();
        }
        return k;
    }

    /**
     * Pesos escalonados por grupos de rango (índice 0 = rango 1), normalizados a 1:
     * <pre>
     * cabeza = max(1, round(0,2·n)) rangos → CUOTA_CABEZA (0,78), perfil lineal de 1,6 a 1,0
     * medio  = round(0,2·n) siguientes     → CUOTA_MEDIO (0,12) por igual
     * cola   = el resto                    → CUOTA_COLA (0,10) por igual
     * </pre>
     * Si un grupo queda vacío (catálogos pequeños) se renormaliza con los grupos presentes.
     * Ejemplo n = 30 (6 / 6 / 18): perfil 1,6 1,48 1,36 1,24 1,12 1,0 (suma 7,8) → cabeza 0,78 × perfil / 7,8 =
     * 16 %, 14,8 %, 13,6 %, 12,4 %, 11,2 %, 10 %; medio 0,12 / 6 = 2 % cada uno; cola 0,10 / 18 ≈ 0,556 % cada uno.
     */
    public static double[] pesosEscalonados(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("Se necesita al menos un producto");
        }
        int cabeza = Math.max(1, (int) Math.round(FRACCION_GRUPO * n));
        int medio = Math.min(n - cabeza, (int) Math.round(FRACCION_GRUPO * n));
        int cola = n - cabeza - medio;

        double[] perfil = new double[cabeza];
        double sumaPerfil = 0;
        for (int i = 0; i < cabeza; i++) {
            perfil[i] = cabeza == 1
                    ? PERFIL_CABEZA_PRIMERO
                    : PERFIL_CABEZA_PRIMERO - (PERFIL_CABEZA_PRIMERO - PERFIL_CABEZA_ULTIMO) * i / (cabeza - 1);
            sumaPerfil += perfil[i];
        }
        double total = CUOTA_CABEZA + (medio > 0 ? CUOTA_MEDIO : 0) + (cola > 0 ? CUOTA_COLA : 0);

        double[] pesos = new double[n];
        for (int i = 0; i < cabeza; i++) {
            pesos[i] = CUOTA_CABEZA * perfil[i] / sumaPerfil / total;
        }
        for (int i = cabeza; i < cabeza + medio; i++) {
            pesos[i] = CUOTA_MEDIO / medio / total;
        }
        for (int i = cabeza + medio; i < n; i++) {
            pesos[i] = CUOTA_COLA / cola / total;
        }
        return pesos;
    }

    /**
     * Participación del {@code fraccion} superior: ordena de mayor a menor, toma los primeros ⌈fraccion × n⌉ y divide su
     * suma entre el total. Valores negativos (devoluciones netas) cuentan como 0. Total 0 → 0.
     * Ejemplo: [50, 30, 10, 5, 5], fracción 0,2 → ⌈1⌉ = 1 → 50 / 100 = 0,5.
     */
    public static double participacionSuperior(double[] valores, double fraccion) {
        if (fraccion <= 0 || fraccion > 1) {
            throw new IllegalArgumentException("La fracción debe estar en (0, 1]");
        }
        if (valores.length == 0) {
            return 0;
        }
        double[] ordenados = Arrays.stream(valores).map(v -> Math.max(0, v)).sorted().toArray();
        double total = Arrays.stream(ordenados).sum();
        if (total == 0) {
            return 0;
        }
        int k = (int) Math.ceil(fraccion * ordenados.length - 1e-9);
        double superior = 0;
        for (int i = ordenados.length - 1; i >= ordenados.length - k; i--) {
            superior += ordenados[i];
        }
        return superior / total;
    }

    /** S 0,20; M 0,35; L 0,30; XL 0,15; UNICA 1; XS y XXL 0 (el catálogo semilla no las usa). */
    public static double participacionTalla(Talla talla) {
        return switch (talla) {
            case S -> 0.20;
            case M -> 0.35;
            case L -> 0.30;
            case XL -> 0.15;
            case UNICA -> 1.0;
            case XS, XXL -> 0.0;
        };
    }

    /** Primer color 0,5; los demás reparten 0,5 por igual. Un solo color → 1. Ejemplo 3 colores: 0,5 / 0,25 / 0,25. */
    public static double participacionColor(int indice, int totalColores) {
        if (totalColores < 1 || indice < 0 || indice >= totalColores) {
            throw new IllegalArgumentException("Índice de color fuera de rango");
        }
        if (totalColores == 1) {
            return 1.0;
        }
        return indice == 0 ? PARTICIPACION_PRIMER_COLOR : (1 - PARTICIPACION_PRIMER_COLOR) / (totalColores - 1);
    }

    /**
     * Precio con descuento redondeado a la centena (HALF_UP), escala 2, nunca mayor que la lista ni menor o igual a 0.
     * Ejemplo: 79.900 con 10 % → 71.910 → 71.900.
     */
    public static BigDecimal precioConDescuento(BigDecimal precioLista, int porcentaje) {
        if (precioLista == null || precioLista.signum() <= 0) {
            throw new IllegalArgumentException("El precio de lista debe ser positivo");
        }
        if (porcentaje < 0 || porcentaje > 99) {
            throw new IllegalArgumentException("El descuento debe estar entre 0 y 99 %");
        }
        BigDecimal lista = precioLista.setScale(2, RoundingMode.HALF_UP);
        if (porcentaje == 0) {
            return lista;
        }
        BigDecimal cien = BigDecimal.valueOf(100);
        BigDecimal precio = lista.multiply(BigDecimal.valueOf(100 - porcentaje))
                .divide(cien.multiply(cien), 0, RoundingMode.HALF_UP)
                .multiply(cien)
                .setScale(2, RoundingMode.HALF_UP);
        return precio.signum() <= 0 || precio.compareTo(lista) > 0 ? lista : precio;
    }

    // ------------------------------------------------------------------ esperanzas (sin aleatoriedad)

    /** Meses calendario desde el mes de inicio (el mes de inicio es 0). */
    public int mesesDesdeInicio(LocalDate dia) {
        validarDia(dia);
        return (int) ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(dia));
    }

    /** λ de tickets del día = base × factorMes × factorDía × 1,015^mes. */
    public double lambdaTickets(LocalDate dia) {
        return ticketsDiaBase * factorMes(dia.getMonth()) * factorDiaSemana(dia.getDayOfWeek())
                * tendencia(mesesDesdeInicio(dia));
    }

    /** {@code true} si el producto es estancado y el día cae en los últimos 90 días de la historia. */
    public boolean estaEstancado(ProductoSemilla producto, LocalDate dia) {
        return producto.estancado() && !dia.isBefore(inicioEstancamiento);
    }

    /** Primer día en que los productos estancados dejan de vender. */
    public LocalDate inicioEstancamiento() {
        return inicioEstancamiento;
    }

    /** Peso del producto ese día, renormalizado entre los productos que venden (estancados en 0). */
    public double pesoEfectivo(ProductoSemilla producto, LocalDate dia) {
        double[] pesos = pesosDelDia(dia);
        int indice = indiceDe(producto);
        double suma = Arrays.stream(pesos).sum();
        return suma == 0 ? 0 : pesos[indice] / suma;
    }

    /**
     * Unidades esperadas del producto en el día: λ × 1,38 líneas × 1,15 unidades × peso efectivo. Es una aproximación
     * (elegir productos distintos dentro de un ticket aplana un poco la distribución): sirve para el stock inicial, el
     * stock mínimo y el tamaño de las reposiciones.
     */
    public double demandaEsperadaDiaria(ProductoSemilla producto, LocalDate dia) {
        return lambdaTickets(dia) * LINEAS_ESPERADAS * CANTIDAD_ESPERADA * pesoEfectivo(producto, dia);
    }

    /** Unidades esperadas del producto en {@code dias} días consecutivos desde {@code desde}. */
    public double demandaEsperada(ProductoSemilla producto, LocalDate desde, int dias) {
        if (dias < 0) {
            throw new IllegalArgumentException("Los días no pueden ser negativos");
        }
        double suma = 0;
        for (int i = 0; i < dias; i++) {
            suma += demandaEsperadaDiaria(producto, desde.plusDays(i));
        }
        return suma;
    }

    /** Participación de una variante dentro de su producto: talla × color. */
    public static double participacionVariante(ProductoSemilla producto, Talla talla, int indiceColor) {
        if (!producto.tallas().contains(talla)) {
            throw new IllegalArgumentException(producto.codigo() + " no se vende en talla " + talla);
        }
        return participacionTalla(talla) * participacionColor(indiceColor, producto.colores().size());
    }

    // ------------------------------------------------------------------ muestreo

    /**
     * Tickets del día ordenados por hora (sin tickets vacíos). Orden de consumo de aleatorios (fijo): número de tickets; por ticket, hora y
     * luego sus líneas (número de líneas; por línea producto, talla, color, cantidad y descuento).
     */
    public List<Ticket> ticketsDelDia(LocalDate dia) {
        int cantidad = poisson(lambdaTickets(dia), random);
        double[] pesosDia = pesosDelDia(dia);
        List<Ticket> tickets = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            LocalTime hora = APERTURA.plusMinutes(random.nextInt(MINUTOS_ABIERTO));
            List<Linea> lineas = lineasDeTicket(pesosDia);
            if (!lineas.isEmpty()) {  // vacío solo si ningún producto vende ese día (todos estancados)
                tickets.add(new Ticket(hora, lineas));
            }
        }
        tickets.sort(Comparator.comparing(Ticket::hora));  // estable: empates conservan el orden de generación
        return List.copyOf(tickets);
    }

    private List<Linea> lineasDeTicket(double[] pesosDia) {
        int numeroLineas = 1 + elegirIndice(PROBABILIDAD_LINEAS, random.nextDouble());
        double[] disponibles = pesosDia.clone();
        List<Linea> lineas = new ArrayList<>(numeroLineas);
        for (int i = 0; i < numeroLineas; i++) {
            double suma = Arrays.stream(disponibles).sum();
            if (suma <= 0) {
                break;  // no quedan productos que vendan ese día
            }
            int indice = elegirIndice(disponibles, random.nextDouble() * suma);
            disponibles[indice] = 0;  // productos distintos dentro del ticket
            ProductoSemilla producto = productos.get(indice);
            Talla talla = elegirTalla(producto);
            int indiceColor = elegirColor(producto.colores().size());
            int cantidad = random.nextDouble() < PROBABILIDAD_CANTIDAD_DOS ? 2 : 1;
            int descuento = random.nextDouble() < PROBABILIDAD_DESCUENTO
                    ? DESCUENTO_MINIMO + random.nextInt(DESCUENTO_MAXIMO - DESCUENTO_MINIMO + 1)
                    : 0;
            lineas.add(new Linea(producto, talla, producto.colores().get(indiceColor), indiceColor, cantidad,
                    descuento));
        }
        return List.copyOf(lineas);
    }

    private Talla elegirTalla(ProductoSemilla producto) {
        List<Talla> tallas = producto.tallas();
        if (tallas.size() == 1) {
            return tallas.getFirst();
        }
        double[] pesos = new double[tallas.size()];
        for (int i = 0; i < pesos.length; i++) {
            pesos[i] = participacionTalla(tallas.get(i));
        }
        return tallas.get(elegirIndice(pesos, random.nextDouble() * Arrays.stream(pesos).sum()));
    }

    private int elegirColor(int totalColores) {
        if (random.nextDouble() < PARTICIPACION_PRIMER_COLOR) {
            return 0;
        }
        return 1 + random.nextInt(totalColores - 1);
    }

    /** Búsqueda lineal en la acumulada: primer índice cuya suma acumulada supera {@code u}. Ignora pesos 0. */
    private static int elegirIndice(double[] pesos, double u) {
        double acumulado = 0;
        int ultimoPositivo = -1;
        for (int i = 0; i < pesos.length; i++) {
            if (pesos[i] <= 0) {
                continue;
            }
            acumulado += pesos[i];
            ultimoPositivo = i;
            if (u < acumulado) {
                return i;
            }
        }
        return ultimoPositivo;  // u ≈ suma por redondeo de punto flotante
    }

    private double[] pesosDelDia(LocalDate dia) {
        validarDia(dia);
        double[] pesos = pesosBase.clone();
        for (int i = 0; i < pesos.length; i++) {
            if (estaEstancado(productos.get(i), dia)) {
                pesos[i] = 0;
            }
        }
        return pesos;
    }

    private int indiceDe(ProductoSemilla producto) {
        int indice = productos.indexOf(producto);
        if (indice < 0) {
            throw new IllegalArgumentException("Producto fuera del catálogo: " + producto.codigo());
        }
        return indice;
    }

    private void validarDia(LocalDate dia) {
        Objects.requireNonNull(dia, "dia");
        if (dia.isBefore(inicio)) {
            throw new IllegalArgumentException("El día " + dia + " es anterior al inicio de la historia " + inicio);
        }
    }

    /** Un cliente que compra: hora y de 1 a 3 líneas de productos distintos. */
    public record Ticket(LocalTime hora, List<Linea> lineas) {
    }

    /**
     * Una línea pedida. {@code descuentoPorcentaje} 0 = precio de lista; el generador aplica
     * {@link #precioConDescuento} sobre el {@code precioVenta} vigente ese día.
     */
    public record Linea(ProductoSemilla producto, Talla talla, String color, int indiceColor, int cantidad,
                        int descuentoPorcentaje) {
    }
}
