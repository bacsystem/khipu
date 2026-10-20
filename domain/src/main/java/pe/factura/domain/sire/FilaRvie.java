package pe.factura.domain.sire;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.Monedas;
import pe.factura.domain.documento.Nota;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.documento.Totales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Una fila del Registro de Ventas e Ingresos Electrónico (RVIE) del SIRE (#322): un comprobante de la empresa, con los 40 campos de la propuesta en su orden. Se
 * arma de lo que khipu emitió ({@link #de}); cómo se escribe cada campo lo decide {@link FormatoRvie}. Los supuestos sobre el Anexo 3, que el repositorio todavía
 * no tiene, están en {@link FormatoRvie} y en {@code deploy/README.md} §15.
 *
 * <p>Reglas, en un solo lugar:
 * <ul>
 *   <li><b>Qué entra</b> ({@link #INCLUIDOS}): lo que SUNAT tiene, aceptado con o sin observaciones, y lo anulado después (una comunicación de baja o, en lo de boleta,
 *       un resumen diario aceptados). Lo que va en camino o falló el envío ({@link #PENDIENTES}) no entra: SUNAT todavía no lo tiene y la propuesta tampoco; se avisa
 *       cuántos son. Lo rechazado, lo descartado, lo fuera de plazo y lo nunca firmado no es una venta.</li>
 *   <li><b>Anulado</b>: estado {@value #ESTADO_ANULADO} e importes en cero, con los datos de identificación del comprobante. Vigente: {@value #ESTADO_VIGENTE}.</li>
 *   <li><b>Nota de crédito</b>: los importes van en negativo ({@link #signo}); la de débito, en positivo. Los campos 29–32 llevan el comprobante modificado y el 34 el
 *       motivo (catálogo 09 o 10).</li>
 *   <li><b>IVAP</b>: la base y el impuesto van en los campos 22 y 23, no en la base gravada (15) ni el IGV (17).</li>
 *   <li><b>Moneda y tipo de cambio</b> (27, 28): los importes van en la moneda del comprobante. En soles el tipo de cambio es {@code 1.000}; fuera de soles, el del
 *       comprobante (#316), o vacío si el emisor no lo informó (el servicio lo advierte antes de escribir).</li>
 *   <li><b>Boletas</b>: una fila por boleta, como las tiene SUNAT (el campo 10, número final de un rango, queda vacío).</li>
 * </ul>
 */
public record FilaRvie(String rucEmisor, String razonSocialEmisor, String periodo, String car, LocalDate fechaEmision, LocalDate fechaVencimiento,
                       String tipoComprobante, String serie, long numero, String tipoDocCliente, String numDocCliente, String nombreCliente,
                       BigDecimal valorExportacion, BigDecimal baseGravada, BigDecimal descuentoBase, BigDecimal igv, BigDecimal descuentoIgv,
                       BigDecimal exonerado, BigDecimal inafecto, BigDecimal isc, BigDecimal baseIvap, BigDecimal ivap, BigDecimal icbper,
                       BigDecimal otrosTributos, BigDecimal total, String moneda, BigDecimal tipoCambio,
                       LocalDate fechaModificado, String tipoModificado, String serieModificado, Long numeroModificado, String tipoNota,
                       String estado, BigDecimal valorGratuito, String tipoOperacion) {

    /** Lo que va al registro: lo que SUNAT tiene. */
    public static final Set<EstadoDocumento> INCLUIDOS = Collections.unmodifiableSet(EnumSet.of(EstadoDocumento.ACEPTADO, EstadoDocumento.ACEPTADO_CON_OBS, EstadoDocumento.ANULADO));
    /** Lo que todavía puede llegar a SUNAT: no va al registro, pero se avisa cuántos son para que el archivo no parezca completo. */
    public static final Set<EstadoDocumento> PENDIENTES = Collections.unmodifiableSet(EnumSet.of(EstadoDocumento.FIRMADO, EstadoDocumento.ENVIADO, EstadoDocumento.ERROR_ENVIO,
            EstadoDocumento.PENDIENTE_AGRUPACION));

    public static final String ESTADO_VIGENTE = "1";
    public static final String ESTADO_ANULADO = "2";

    /** Largos máximos de los campos de texto: los del propio comprobante electrónico (RUC 11, serie 4, documento del cliente 15, razón social 1500, regla 2022). */
    static final int MAX_NOMBRE = 1500;
    static final int MAX_DOCUMENTO = 15;

    /**
     * La fila de un comprobante. {@code fechaModificado} es la fecha de emisión del comprobante que modifica una nota (campo 29), que la nota no guarda; nula si no
     * es una nota o si khipu no lo tiene.
     */
    public static FilaRvie de(String rucEmisor, String razonSocialEmisor, PeriodoSire periodo, Comprobante c, LocalDate fechaModificado) {
        if (!INCLUIDOS.contains(c.estado()))
            throw new IllegalArgumentException("El comprobante " + c.serie() + "-" + c.numero() + " está " + c.estado() + " y no va al registro de ventas");
        if (c.numero() == null) throw new IllegalArgumentException("Un comprobante sin número no va al registro de ventas");
        boolean anulado = c.estado() == EstadoDocumento.ANULADO;
        Totales t = c.totales();
        int signo = signo(c.tipo());
        boolean ivap = t.tieneIvap();
        Nota n = c.nota();
        String tipo = c.tipo().codigo();
        return new FilaRvie(rucEmisor, FormatoRvie.texto(razonSocialEmisor, MAX_NOMBRE), periodo.aaaamm(), car(rucEmisor, tipo, c.serie(), c.numero()), c.fechaEmision(), c.fechaVencimiento(),
                tipo, c.serie(), c.numero(), FormatoRvie.texto(c.receptor().tipoDoc(), 1), FormatoRvie.texto(c.receptor().numDoc(), MAX_DOCUMENTO), FormatoRvie.texto(c.receptor().razonSocial(), MAX_NOMBRE),
                monto(t.exportacion(), signo, anulado), monto(ivap ? null : t.gravado(), signo, anulado), cero(), monto(t.igv(), signo, anulado), cero(),
                monto(t.exonerado(), signo, anulado), monto(t.inafecto(), signo, anulado), monto(t.isc(), signo, anulado),
                monto(ivap ? t.gravado() : null, signo, anulado), monto(t.ivap(), signo, anulado), monto(t.icbper(), signo, anulado),
                monto(t.otrosTributos().add(t.totalCargos()), signo, anulado), monto(t.total(), signo, anulado),
                c.moneda(), Monedas.SOLES.equals(c.moneda()) ? BigDecimal.ONE : c.tipoCambio(),
                n == null ? null : fechaModificado, n == null ? null : n.tipoAfectado().codigo(), n == null ? null : n.serieAfectada(), n == null ? null : n.numeroAfectado(),
                n == null ? null : n.motivo(), anulado ? ESTADO_ANULADO : ESTADO_VIGENTE, monto(t.gratuito(), signo, anulado), c.tipoOperacion());
    }

    /** −1 en la nota de crédito, que disminuye la venta del periodo; +1 en lo demás. */
    static int signo(TipoDocumento tipo) { return tipo == TipoDocumento.NOTA_CREDITO ? -1 : 1; }

    /** Código de anotación de registro: RUC + tipo + serie + número en 10 dígitos (supuesto, ver {@link FormatoRvie}). */
    static String car(String ruc, String tipo, String serie, long numero) { return ruc + tipo + serie + "%010d".formatted(numero); }

    private static BigDecimal monto(BigDecimal v, int signo, boolean anulado) {
        if (anulado || v == null) return cero();
        return signo < 0 ? v.negate() : v;
    }

    private static BigDecimal cero() { return BigDecimal.ZERO.setScale(2); }

    /** Los 40 campos ya escritos, en el orden del Anexo 3. Los que khipu no tiene (10, 33, 36, 39, 40) van vacíos. */
    public List<String> campos() {
        List<String> f = new ArrayList<>(FormatoRvie.CAMPOS);
        f.add(rucEmisor);                                   // 1  RUC del emisor
        f.add(razonSocialEmisor);                           // 2  razón social del emisor
        f.add(periodo);                                     // 3  periodo AAAAMM
        f.add(car);                                         // 4  CAR
        f.add(FormatoRvie.fecha(fechaEmision));             // 5  fecha de emisión
        f.add(FormatoRvie.fecha(fechaVencimiento));         // 6  fecha de vencimiento o pago
        f.add(tipoComprobante);                             // 7  tipo de comprobante (catálogo 01)
        f.add(serie);                                       // 8  serie
        f.add(String.valueOf(numero));                      // 9  número (o inicial de un rango)
        f.add("");                                          // 10 número final del rango
        f.add(tipoDocCliente);                              // 11 tipo de documento del cliente (catálogo 06)
        f.add(numDocCliente);                               // 12 número de documento del cliente
        f.add(nombreCliente);                               // 13 razón social o nombre del cliente
        f.add(FormatoRvie.importe(valorExportacion));       // 14 valor facturado de la exportación
        f.add(FormatoRvie.importe(baseGravada));            // 15 base imponible gravada
        f.add(FormatoRvie.importe(descuentoBase));          // 16 descuento de la base imponible
        f.add(FormatoRvie.importe(igv));                    // 17 IGV / IPM
        f.add(FormatoRvie.importe(descuentoIgv));           // 18 descuento del IGV / IPM
        f.add(FormatoRvie.importe(exonerado));              // 19 exonerado
        f.add(FormatoRvie.importe(inafecto));               // 20 inafecto
        f.add(FormatoRvie.importe(isc));                    // 21 ISC
        f.add(FormatoRvie.importe(baseIvap));               // 22 base imponible del IVAP
        f.add(FormatoRvie.importe(ivap));                   // 23 IVAP
        f.add(FormatoRvie.importe(icbper));                 // 24 ICBPER
        f.add(FormatoRvie.importe(otrosTributos));          // 25 otros tributos y cargos que no forman parte de la base
        f.add(FormatoRvie.importe(total));                  // 26 importe total
        f.add(moneda);                                      // 27 moneda (catálogo 02)
        f.add(FormatoRvie.tipoCambio(tipoCambio));          // 28 tipo de cambio
        f.add(FormatoRvie.fecha(fechaModificado));          // 29 fecha de emisión del comprobante modificado
        f.add(tipoModificado == null ? "" : tipoModificado);   // 30 tipo del comprobante modificado
        f.add(serieModificado == null ? "" : serieModificado); // 31 serie del comprobante modificado
        f.add(numeroModificado == null ? "" : String.valueOf(numeroModificado)); // 32 número del comprobante modificado
        f.add("");                                          // 33 identificación del contrato de colaboración (proyecto)
        f.add(tipoNota == null ? "" : tipoNota);            // 34 tipo de nota (motivo, catálogo 09/10)
        f.add(estado);                                      // 35 estado del comprobante
        f.add("");                                          // 36 valor FOB embarcado
        f.add(FormatoRvie.importe(valorGratuito));          // 37 valor de las operaciones gratuitas
        f.add(tipoOperacion == null ? "" : tipoOperacion);  // 38 tipo de operación (catálogo 51)
        f.add("");                                          // 39 DAM / CP
        f.add("");                                          // 40 CLU (campo libre)
        return f;
    }

    /** Lo que la conciliación compara de esta fila con la de SUNAT. */
    public ImportesRvie importes() {
        return new ImportesRvie(tipoComprobante, serie, numero, fechaEmision, moneda, estado, baseGravada, igv, exonerado, inafecto, total);
    }
}
