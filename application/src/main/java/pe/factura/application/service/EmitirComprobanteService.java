package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.Nota;
import pe.factura.domain.documento.TasaIgv;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.documento.Totales;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class EmitirComprobanteService implements EmitirComprobanteUseCase {
    private final ComprobanteRepository comprobantes;
    private final SerieRepository series;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final UblGenerator ubl;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final EnviarDocumentoUseCase enviar;
    private final UnitOfWork uow;
    private final Clock clock;
    private final EstablecimientoRepository establecimientos;


    @Override
    public Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd) {
        Tenant tenant = tenantListo(tenantId, cmd.enviarAutomatico());
        List<Anticipo> anticipos = cmd.anticipos() == null ? List.of() : cmd.anticipos();
        // La cuenta de detracciones puede omitirse en la factura si la empresa la tiene configurada.
        Detraccion detraccion = cmd.detraccion() != null && cmd.detraccion().sinCuenta() ? cmd.detraccion().conCuenta(tenant.cuentaDetracciones()) : cmd.detraccion();

        Comprobante c = Comprobante.factura(tenantId, cmd.serie(), cmd.fechaEmision(), cmd.moneda(), cmd.tipoOperacion(), cmd.receptor(), cmd.items())
                .fechaVencimiento(cmd.fechaVencimiento())
                .formaPago(cmd.formaPago())
                .descuentoGlobal(cmd.descuentoGlobal())
                .cargos(cmd.cargos())
                .detraccion(detraccion)
                .retencion(cmd.retencionIgv())
                .percepcion(cmd.percepcion())
                .anticipos(anticipos)
                .referencias(cmd.referencias())
                .redondeo(cmd.redondeo())
                .tasaIgv(TasaIgv.vigente(cmd.fechaEmision(), tenant.padronTasaEspecialIgv()))
                .leyendas(cmd.leyendas())
                .exportacion(cmd.exportacion())
                .crear(clock);
        c.anotar(cmd.observaciones());
        // Dentro de la transacción y con la factura de anticipo bloqueada: dos finales concurrentes no pueden regularizar el mismo anticipo dos veces.
        return emitir(tenant, c, cmd.correlativo(), cmd.enviarAutomatico(), () -> anticipos.forEach(a -> validarFacturaDeAnticipo(tenantId, cmd, a)));
    }

    @Override
    public Comprobante emitirNota(UUID tenantId, EmitirNotaCommand cmd) {
        Tenant tenant = tenantListo(tenantId, cmd.enviarAutomatico());
        Comprobante factura = exigirModificable(comprobantes.buscarPorNumero(tenantId, TipoDocumento.FACTURA, cmd.serieAfectada(), cmd.numeroAfectado()), cmd.serieAfectada(), cmd.numeroAfectado());
        if (cmd.fechaEmision().isBefore(factura.fechaEmision()))
            throw new DomainException("NOTA_INVALIDA", "2885 - La fecha de la nota no puede ser anterior a la de la factura que modifica (" + factura.fechaEmision() + ")");

        Nota nota = new Nota(TipoDocumento.FACTURA, factura.serie(), factura.numero(), cmd.motivo(), cmd.descripcion());
        boolean nc13 = nota.corrigeCuotas(cmd.tipo());
        if (nc13) {
            if (!factura.formaPago().esCredito())
                throw new DomainException("NOTA_INVALIDA", "3260 - El motivo 13 solo aplica a facturas al crédito y " + nota.documentoAfectado() + " es al contado");
            if (cmd.formaPago() != null) cmd.formaPago().validarComoCorreccionDe(factura.totales().total(), factura.fechaEmision());
        } else if (cmd.formaPago() != null) {
            // Fuera de la NC 13 nada valida la forma de pago contra la factura (3320/3321): aceptarla sacaría PaymentTerms sin control.
            throw new DomainException("NOTA_INVALIDA", "forma_pago solo se admite en una nota de crédito con motivo 13 (corrección de cuotas)");
        }
        // Nota total: los mismos ítems, descuento y cargos de la factura; así SUNAT ve exactamente los importes que anula (3286 y afines).
        boolean copia = cmd.copiaLaFactura() && !nc13;
        if (copia) {
            // Los anticipos no viajan en una nota (el CreditNote UBL no tiene PrepaidPayment): copiar los ítems daría el bruto y
            // SUNAT compara contra el neto de la factura (3286). El emisor debe indicar los ítems por el importe que anula.
            if (!factura.anticipos().isEmpty())
                throw new DomainException("NOTA_INVALIDA", "La factura " + nota.documentoAfectado() + " regularizó anticipos: indique los items de la nota por el importe neto ("
                        + factura.totales().total() + ") en vez de omitirlos");
            // Sin ítems se copian descuento y cargos de la factura; aceptar otros en silencio daría una nota distinta de la pedida.
            if (cmd.descuentoGlobal() != null || (cmd.cargos() != null && !cmd.cargos().isEmpty()))
                throw new DomainException("NOTA_INVALIDA", "descuento_global y cargos solo se admiten junto con items; sin items la nota copia los de la factura");
        }
        // La nota hereda la tasa de IGV de la factura: si el emisor entró o salió del padrón después, la factura no cambia de tasa.
        Comprobante c = Comprobante.nota(tenantId, cmd.tipo(), cmd.serie(), cmd.fechaEmision(), nota, factura.receptor(), copia ? factura.items() : cmd.items())
                .moneda(factura.moneda())
                .tipoOperacion(factura.tipoOperacion())
                .formaPago(cmd.formaPago())
                .descuentoGlobal(copia ? factura.descuentoGlobal() : cmd.descuentoGlobal())
                .cargos(copia ? factura.cargos() : cmd.cargos())
                .tasaIgv(factura.tasaIgv())
                .exportacion(factura.exportacion())
                .crear(clock);
        c.anotar(cmd.observaciones());
        // Releída con lock de fila dentro de la transacción: una baja que se cuele entre la lectura de arriba y aquí no deja pasar la nota,
        // y el acumulado de NC se lee con la factura bloqueada, así que dos NC concurrentes no pueden acreditarla dos veces.
        return emitir(tenant, c, cmd.correlativo(), cmd.enviarAutomatico(), () -> {
            Comprobante bloqueada = exigirModificable(comprobantes.bloquearPorNumero(tenantId, TipoDocumento.FACTURA, factura.serie(), factura.numero()), factura.serie(), factura.numero());
            if (cmd.tipo() == TipoDocumento.NOTA_CREDITO) exigirQueNoSupereALaFactura(c.totales(), bloqueada, acreditadoPorNotas(tenantId, bloqueada));
        });
    }

    /**
     * Suma de las notas de crédito ya emitidas sobre la factura que siguen vigentes: SUNAT no cruza una NC con las anteriores
     * (3286/3503 comparan nota por nota), así que sin esto una segunda NC total pasa y el cliente acredita dos veces (#83).
     * Una NC RECHAZADO o INVALIDO no acreditó nada; una pendiente de envío o en reintento cuenta, igual que en {@link #validarFacturaDeAnticipo}.
     */
    private Acreditado acreditadoPorNotas(UUID tenantId, Comprobante factura) {
        Acreditado suma = Acreditado.CERO;
        for (Comprobante n : comprobantes.notasDe(tenantId, factura.serie(), factura.numero())) {
            if (n.tipo() != TipoDocumento.NOTA_CREDITO) continue;
            if (n.estado() == EstadoDocumento.RECHAZADO || n.estado() == EstadoDocumento.INVALIDO || n.estado() == EstadoDocumento.ANULADO) continue;
            suma = suma.mas(n.totales());
        }
        return suma;
    }

    /** Importes ya acreditados por NC vigentes sobre una factura, en los conceptos que SUNAT limita (3286, 3503). */
    private record Acreditado(BigDecimal total, BigDecimal gravado, BigDecimal igv, BigDecimal ivap, BigDecimal exonerado, BigDecimal inafecto, BigDecimal gratuito, BigDecimal exportacion) {
        static final Acreditado CERO = new Acreditado(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Acreditado mas(Totales t) {
            return new Acreditado(total.add(t.total()), gravado.add(t.gravado()), igv.add(t.igv()), ivap.add(t.ivap()), exonerado.add(t.exonerado()), inafecto.add(t.inafecto()), gratuito.add(t.gratuito()), exportacion.add(t.exportacion()));
        }
    }

    /** La factura serie-número debe existir en la empresa, estar aceptada por SUNAT y no anulada (2119/2120). */
    private static Comprobante exigirModificable(Optional<Comprobante> buscada, String serie, long numero) {
        Comprobante factura = buscada.orElseThrow(() -> new DomainException("NOTA_INVALIDA", "2119 - La factura " + serie + "-" + numero + " no existe en esta empresa"));
        if (factura.estado() == EstadoDocumento.ANULADO)
            throw new DomainException("NOTA_INVALIDA", "2120 - La factura " + serie + "-" + numero + " está anulada");
        if (factura.estado() != EstadoDocumento.ACEPTADO && factura.estado() != EstadoDocumento.ACEPTADO_CON_OBS)
            throw new DomainException("NOTA_INVALIDA", "2119 - La factura " + serie + "-" + numero + " no está aceptada por SUNAT (estado " + factura.estado() + ")");
        return factura;
    }

    private Tenant tenantListo(UUID tenantId, boolean enviarAutomatico) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirListoParaEmitir(LocalDate.now(clock));
        // Si se va a enviar de inmediato, las credenciales SOL deben existir antes de consumir un número
        // o persistir el documento: de lo contrario quedaría un FIRMADO huérfano con numeración gastada.
        if (enviarAutomatico) tenant.exigirCredencialesSol();
        return tenant;
    }

    /**
     * Reglas 3286 (importe total) y 3503 (base e impuesto por tributo) de la hoja NotaCredito2_0: la NC no puede superar
     * (tolerancia ±1) los importes de la factura que modifica. SUNAT lo comprueba nota por nota; khipu descuenta además lo
     * ya acreditado por las NC anteriores, para que varias notas no sumen más que la factura (#83).
     */
    private static void exigirQueNoSupereALaFactura(Totales nc, Comprobante factura, Acreditado previo) {
        Totales f = factura.totales();
        BigDecimal tol = BigDecimal.ONE;
        record Limite(String concepto, String regla, BigDecimal nota, BigDecimal factura, BigDecimal acreditado) {}
        for (Limite l : List.of(new Limite("importe total", "3286", nc.total(), f.total(), previo.total()),
                new Limite("valor de venta gravado", "3503", nc.gravado(), f.gravado(), previo.gravado()),
                new Limite("IGV", "3503", nc.igv(), f.igv(), previo.igv()),
                new Limite("IVAP", "3503", nc.ivap(), f.ivap(), previo.ivap()),
                new Limite("valor de venta exonerado", "3503", nc.exonerado(), f.exonerado(), previo.exonerado()),
                new Limite("valor de venta inafecto", "3503", nc.inafecto(), f.inafecto(), previo.inafecto()),
                new Limite("valor de las operaciones gratuitas", "3503", nc.gratuito(), f.gratuito(), previo.gratuito()),
                new Limite("valor de venta de exportación", "3503", nc.exportacion(), f.exportacion(), previo.exportacion()))) {
            if (l.nota().add(l.acreditado()).subtract(l.factura()).compareTo(tol) > 0)
                throw new DomainException("NOTA_INVALIDA", l.regla() + " - El " + l.concepto() + " de la nota (" + l.nota() + ") supera el de la factura "
                        + factura.serie() + "-" + factura.numero() + " (" + l.factura() + ")"
                        + (l.acreditado().signum() > 0 ? ": ya acreditado " + l.acreditado() + " en otras notas de crédito" : ""));
        }
    }

    /** Numera, genera, firma, valida, guarda y (si procede) envía; {@code enTransaccion} corre con el número ya asignado y la serie bloqueada. */
    private Comprobante emitir(Tenant tenant, Comprobante c, Long correlativo, boolean enviarAutomatico, Runnable enTransaccion) {
        UUID tenantId = tenant.id();
        Comprobante firmado = uow.ejecutar(() -> {
            long numero;
            if (correlativo != null) {
                if (comprobantes.buscarPorNumero(tenantId, c.tipo(), c.serie(), correlativo).isPresent())
                    throw new DomainException("DUPLICADO", "Ya existe " + c.serie() + "-" + correlativo);
                // La serie avanza hasta el correlativo explícito para que la siguiente emisión automática no lo reutilice.
                series.avanzarHasta(tenantId, c.tipo(), c.serie(), correlativo);
                numero = correlativo;
            } else {
                numero = series.siguienteNumero(tenantId, c.tipo(), c.serie());
            }
            c.asignarNumero(numero, tenant.ruc());
            enTransaccion.run();

            // El XML lleva el domicilio del establecimiento de la serie (#80); con la serie en 0000, el fiscal del tenant.
            String xml = ubl.generar(c, EmisorDeSerie.paraEmitir(series, establecimientos, tenant, c));
            FirmaResultado firma = signer.firmar(xml, tenant.certificado());
            xsd.validar(firma.xmlFirmado(), c.tipo());

            String key = tenantId + "/" + c.fechaEmision().getYear() + "/" + String.format("%02d", c.fechaEmision().getMonthValue())
                    + "/" + c.nombreArchivo() + ".xml";
            storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
            c.firmar(firma.hash(), key);
            comprobantes.guardar(c);
            return c;
        });

        if (!enviarAutomatico) return firmado;
        // EnviarDocumentoService persiste el resultado y, si queda en ERROR_ENVIO, programa el reintento
        // en el outbox dentro de la misma transacción.
        return enviar.enviar(tenantId, firmado.id());
    }

    /**
     * La factura de anticipo debe ser de esta empresa y estar aceptada por SUNAT (regla 3218), al mismo cliente y en la misma
     * moneda (2071), y el monto que se regulariza —sumado a lo ya regularizado en otras facturas finales— no puede superar lo
     * que aquella facturó en esa afectación: SUNAT no cruza anticipos entre comprobantes, así que un doble descuento pasaría inadvertido.
     */
    private void validarFacturaDeAnticipo(UUID tenantId, EmitirFacturaCommand cmd, Anticipo a) {
        Comprobante origen = comprobantes.bloquearPorNumero(tenantId, TipoDocumento.FACTURA, a.serie(), a.numero())
                .orElseThrow(() -> new DomainException("ANTICIPO_INVALIDO", "3218 - La factura de anticipo " + a.comprobante() + " no existe en esta empresa"));
        if (origen.estado() != EstadoDocumento.ACEPTADO && origen.estado() != EstadoDocumento.ACEPTADO_CON_OBS)
            throw new DomainException("ANTICIPO_INVALIDO", "3218 - La factura de anticipo " + a.comprobante() + " no está aceptada por SUNAT (estado " + origen.estado() + ")");
        if (!origen.moneda().equals(cmd.moneda()))
            throw new DomainException("ANTICIPO_INVALIDO", "2071 - La factura de anticipo " + a.comprobante() + " es en " + origen.moneda() + " y esta factura en " + cmd.moneda());
        if (!origen.receptor().numDoc().equals(cmd.receptor().numDoc()))
            throw new DomainException("ANTICIPO_INVALIDO", "La factura de anticipo " + a.comprobante() + " fue emitida a otro cliente (RUC " + origen.receptor().numDoc() + ")");
        BigDecimal facturado = switch (a.afectacion()) {
            case GRAVADO -> origen.totales().gravado();
            case EXONERADO -> origen.totales().exonerado();
            case INAFECTO -> origen.totales().inafecto();
        };
        BigDecimal yaRegularizado = comprobantes.montoRegularizado(tenantId, a.serie(), a.numero());
        if (yaRegularizado.add(a.monto()).compareTo(facturado) > 0)
            throw new DomainException("ANTICIPO_INVALIDO", "El anticipo " + a.comprobante() + " (" + a.monto() + ") supera el valor de venta "
                    + a.afectacion().name().toLowerCase() + " de esa factura (" + facturado + ")"
                    + (yaRegularizado.signum() > 0 ? ": ya se regularizaron " + yaRegularizado + " en otras facturas" : ""));
    }
}
