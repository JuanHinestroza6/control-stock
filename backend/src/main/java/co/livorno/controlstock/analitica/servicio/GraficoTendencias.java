package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.dto.PuntoTendencia;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Gráfico de líneas del ingreso neto mensual (serie actual y mismo mes del año anterior, punteada) dibujado con Java2D
 * sobre un {@link BufferedImage}; funciona sin pantalla (headless). Se inserta en el PDF de tendencias (CU-05).
 */
final class GraficoTendencias {

    private static final int ANCHO = 1100;
    private static final int ALTO = 340;
    private static final int MARGEN_IZQ = 100;
    private static final int MARGEN_DER = 24;
    private static final int MARGEN_SUP = 44;
    private static final int MARGEN_INF = 46;
    private static final int DIVISIONES = 4;
    private static final Color ACTUAL = new Color(0x25, 0x63, 0xEB);
    private static final Color ANTERIOR = new Color(0x6B, 0x72, 0x80);
    private static final Color REJILLA = new Color(0xE5, 0xE7, 0xEB);
    private static final Color TEXTO = new Color(0x37, 0x41, 0x51);
    private static final DecimalFormatSymbols SIMBOLOS = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO"));

    private GraficoTendencias() {}

    /** PNG del gráfico; requiere al menos un punto. */
    static byte[] png(List<PuntoTendencia> puntos) {
        BufferedImage imagen = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ANCHO, ALTO);
            dibujar(g, puntos);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagen, "png", salida);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el gráfico de tendencias", e);
        }
        return salida.toByteArray();
    }

    private static void dibujar(Graphics2D g, List<PuntoTendencia> puntos) {
        int x0 = MARGEN_IZQ;
        int x1 = ANCHO - MARGEN_DER;
        int yTop = MARGEN_SUP;
        int yBase = ALTO - MARGEN_INF;
        double maximo = escalaMaxima(puntos);
        Font fuente = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        g.setFont(fuente);
        FontMetrics fm = g.getFontMetrics();

        g.setColor(TEXTO);
        g.setFont(fuente.deriveFont(Font.BOLD, 14f));
        g.drawString("Ingreso neto mensual", x0, 22);
        g.setFont(fuente);

        // rejilla y etiquetas del eje Y
        DecimalFormat miles = new DecimalFormat("#,##0", SIMBOLOS);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= DIVISIONES; i++) {
            int y = yBase - (int) Math.round((yBase - yTop) * (double) i / DIVISIONES);
            g.setColor(REJILLA);
            g.drawLine(x0, y, x1, y);
            g.setColor(TEXTO);
            String etiqueta = miles.format(maximo * i / DIVISIONES);
            g.drawString(etiqueta, x0 - 8 - fm.stringWidth(etiqueta), y + fm.getAscent() / 2 - 1);
        }

        // eje X: a lo sumo ~12 etiquetas
        int n = puntos.size();
        int paso = Math.max(1, (int) Math.ceil(n / 12.0));
        for (int i = 0; i < n; i += paso) {
            String etiqueta = puntos.get(i).periodo();
            int x = coordenadaX(i, n, x0, x1);
            g.setColor(TEXTO);
            g.drawString(etiqueta, x - fm.stringWidth(etiqueta) / 2, yBase + 8 + fm.getAscent());
        }

        // serie del año anterior (punteada) y actual
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] {6f, 5f}, 0f));
        g.setColor(ANTERIOR);
        linea(g, puntos, true, maximo, x0, x1, yTop, yBase);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ACTUAL);
        linea(g, puntos, false, maximo, x0, x1, yTop, yBase);

        // leyenda
        int xl = x1 - 330;
        g.setStroke(new BasicStroke(2.6f));
        g.setColor(ACTUAL);
        g.drawLine(xl, 18, xl + 28, 18);
        g.setColor(TEXTO);
        g.drawString("Periodo", xl + 34, 23);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] {6f, 5f}, 0f));
        g.setColor(ANTERIOR);
        g.drawLine(xl + 110, 18, xl + 138, 18);
        g.setColor(TEXTO);
        g.drawString("Mismo mes del año anterior", xl + 144, 23);
    }

    private static void linea(Graphics2D g, List<PuntoTendencia> puntos, boolean anterior, double maximo, int x0, int x1,
            int yTop, int yBase) {
        int n = puntos.size();
        int xPrev = 0;
        int yPrev = 0;
        for (int i = 0; i < n; i++) {
            int x = coordenadaX(i, n, x0, x1);
            double valor = valor(puntos.get(i), anterior);
            int y = yBase - (int) Math.round((yBase - yTop) * valor / maximo);
            if (i > 0) {
                g.drawLine(xPrev, yPrev, x, y);
            }
            if (!anterior) {
                g.fillOval(x - 3, y - 3, 6, 6);
            }
            xPrev = x;
            yPrev = y;
        }
    }

    private static int coordenadaX(int i, int n, int x0, int x1) {
        if (n == 1) {
            return (x0 + x1) / 2;
        }
        return x0 + (int) Math.round((x1 - x0) * (double) i / (n - 1));
    }

    private static double valor(PuntoTendencia p, boolean anterior) {
        BigDecimal v = anterior ? p.ingresoAnioAnterior() : p.ingreso();
        return v == null ? 0 : v.doubleValue();
    }

    /** Máximo del eje Y: el mayor valor redondeado hacia arriba a 1, 2, 2,5, 5 o 10 por potencia de diez. */
    static double escalaMaxima(List<PuntoTendencia> puntos) {
        double max = 0;
        for (PuntoTendencia p : puntos) {
            max = Math.max(max, Math.max(valor(p, false), valor(p, true)));
        }
        if (max <= 0) {
            return 1;
        }
        double potencia = Math.pow(10, Math.floor(Math.log10(max)));
        for (double m : new double[] {1, 2, 2.5, 5, 10}) {
            if (max <= m * potencia) {
                return m * potencia;
            }
        }
        return 10 * potencia;
    }
}
