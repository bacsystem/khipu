package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.in.ValidarCompradorUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.ComunicacionBaja;
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
    private final EmisorDeSerieRepository emisorDeSerie;
    private final BajaRepository bajas;
    private final IdempotenciaRepository idempotencia;
    private final TopeDelPlan tope;
    private final ValidarCompradorUseCase comprador;

    @Override
    public Emision emitirFactura(UUID tenantId, EmitirFacturaCommand cmd, Idempotencia clave) {
        ClaveEnAlcance enAlcance = clave == null ? null : new ClaveEnAlcance("factura:" + tenantId, clave, "factura");
        Emision repetida = yaEmitidaSiLaHay(tenantId, enAlcance);
        if (repetida != null) return repetida;
        Tenant tenant = tenantListo(tenantId, cmd.enviarAutomatico());
        List<Anticipo> anticipos = cmd.anticipos() == null ? List.of() : cmd.anticipos();
        Comprobante c = armarFactura(tenant, tenantId, cmd, anticipos);
        // Dentro de la transacción y con la factura de anticipo bloqueada: dos finales concurrentes no pueden regularizar el mismo anticipo dos veces.
        return emitir(tenant, c, cmd.correlativo(), cmd.enviarAutomatico(), () -> anticipos.forEach(a -> validarFacturaDeAnticipo(tenantId, cmd, a)), enAlcance);
    }

    /**
     * La clave de idempotencia con su alcance: la misma clave en otra empresa es otra clave, y la de una factura no es la de una nota ({@code documento}
     * solo nombra al comprobante en el mensaje de rechazo).
     */
    private record ClaveEnAlcance(String alcance, Idempotencia clave, String documento) {}

    /**
     * Un reintento se contesta antes de validar: el comprobante ya existe, y que después venciera el plazo de envío (2108), el certificado o que el documento
     * afectado ya no admita notas no es motivo para negarlo, que es justo el caso (un corte de red) para el que existe la clave. Es solo una lectura de lo
     * confirmado; la exclusión entre pedidos simultáneos sigue en la reserva de `emitir`. {@code null} si no hay clave o no se usó antes.
     */
    private Emision yaEmitidaSiLaHay(UUID tenantId, ClaveEnAlcance clave) {
        if (clave == null) return null;
        return idempotencia.buscar(clave.alcance(), clave.clave().clave()).map(previo -> yaEmitida(tenantId, clave, previo)).orElse(null);
    }

    @Override
    public Emision emitirNota(UUID tenantId, EmitirNotaCommand cmd, Idempotencia clave) {
        ClaveEnAlcance enAlcance = clave == null ? null : new ClaveEnAlcance("nota:" + tenantId, clave, "nota");
        Emision repetida = yaEmitidaSiLaHay(tenantId, enAlcance);
        if (repetida != null) return repetida;
        Tenant tenant = tenantListo(tenantId, cmd.enviarAutomatico());
        // #293: como al emitir (#20), la letra de la serie dice qué se modifica: B una boleta, F una factura. La nota se valida antes de buscar el documento,
        // así un motivo que no corresponde (2172, 3259, 4367) se rechaza por lo que es y no por el estado del documento.
        TipoDocumento tipoAfectado = cmd.serieAfectada() != null && cmd.serieAfectada().startsWith("B") ? TipoDocumento.BOLETA : TipoDocumento.FACTURA;
        Nota nota = new Nota(tipoAfectado, cmd.serieAfectada(), cmd.numeroAfectado(), cmd.motivo(), cmd.descripcion());
        nota.validarMotivoPara(cmd.tipo());
        Comprobante afectado = exigirModificable(comprobantes.buscarPorNumero(tenantId, tipoAfectado, cmd.serieAfectada(), cmd.numeroAfectado()), tipoAfectado, cmd.serieAfectada(), cmd.numeroAfectado());
        if (cmd.fechaEmision().isBefore(afectado.fechaEmision()))
            throw new DomainException("NOTA_INVALIDA", "2885 - La fecha de la nota no puede ser anterior a la de la " + nombre(tipoAfectado) + " que modifica (" + afectado.fechaEmision() + ")");

        exigirSinBajaEnCurso(tenantId, afectado);
        boolean nc13 = nota.corrigeCuotas(cmd.tipo());
        if (nc13) {
            if (!afectado.formaPago().esCredito())
                throw new DomainException("NOTA_INVALIDA", "3260 - El motivo 13 solo aplica a facturas al crédito y " + nota.documentoAfectado() + " es al contado");
            if (cmd.formaPago() != null) cmd.formaPago().validarComoCorreccionDe(afectado.totales().total(), afectado.fechaEmision());
        } else if (cmd.formaPago() != null) {
            // Fuera de la NC 13 nada valida la forma de pago contra la factura (3320/3321): aceptarla sacaría PaymentTerms sin control.
            throw new DomainException("NOTA_INVALIDA", "forma_pago solo se admite en una nota de crédito con motivo 13 (corrección de cuotas)");
        }
        // Nota total: los mismos ítems, descuento y cargos del documento; así SUNAT ve exactamente los importes que anula (3286 y afines).
        boolean copia = cmd.copiaLaFactura() && !nc13;
        if (copia) {
            // Los anticipos no viajan en una nota (el CreditNote UBL no tiene PrepaidPayment): copiar los ítems daría el bruto y
            // SUNAT compara contra el neto de la factura (3286). El emisor debe indicar los ítems por el importe que anula.
            if (!afectado.anticipos().isEmpty()) {
                // Con neto 0.00 no hay nota posible: SUNAT rechaza la línea en cero (2369, 2370) y una nota por más que el total (3286). Pedir «items por el neto» sería un callejón.
                if (afectado.totales().total().signum() == 0)
                    throw new DomainException("NOTA_INVALIDA", "La " + nombre(tipoAfectado) + " " + nota.documentoAfectado() + " regularizó anticipos y su importe neto es " + afectado.totales().total()
                            + ": no hay importe que acreditar con una nota (SUNAT rechaza las líneas en cero, 2369 y 2370, y una nota por más que el total, 3286); para anularla use la comunicación de baja");
                throw new DomainException("NOTA_INVALIDA", "La " + nombre(tipoAfectado) + " " + nota.documentoAfectado() + " regularizó anticipos: indique los items de la nota por el importe neto ("
                        + afectado.totales().total() + ") en vez de omitirlos");
            }
            // Sin ítems se copian descuento y cargos del documento; aceptar otros en silencio daría una nota distinta de la pedida.
            if (cmd.descuentoGlobal() != null || (cmd.cargos() != null && !cmd.cargos().isEmpty()))
                throw new DomainException("NOTA_INVALIDA", "descuento_global y cargos solo se admiten junto con items; sin items la nota copia los de la " + nombre(tipoAfectado));
        }
        // La nota hereda la tasa de IGV del documento: si el emisor entró o salió del padrón después, el documento no cambia de tasa. El receptor es el
        // del documento (RCP art. 10 num. 1.3): en una boleta puede no tener documento.
        Comprobante.NotaBuilder builder = Comprobante.nota(tenantId, cmd.tipo(), cmd.serie(), cmd.fechaEmision(), nota, afectado.receptor(), copia ? afectado.items() : cmd.items());
        // #308: las líneas copiadas se validan como las propias (SUNAT cruza la unidad de la nota con el catálogo 03); el mensaje dice cómo emitirla.
        if (copia) builder.copiaDelDocumento();
        Comprobante c = builder
                .moneda(afectado.moneda())
                .tipoCambio(afectado.tipoCambio())
                .tipoOperacion(afectado.tipoOperacion())
                .formaPago(cmd.formaPago())
                .descuentoGlobal(copia ? afectado.descuentoGlobal() : cmd.descuentoGlobal())
                .cargos(copia ? afectado.cargos() : cmd.cargos())
                .tasaIgv(afectado.tasaIgv())
                .exportacion(afectado.exportacion())
                // Con el redondeo del documento la nota total sale exactamente por su PayableAmount (3286 sin tolerancia en facturas).
                .redondeo(copia ? afectado.totales().redondeo() : null)
                .crear(clock);
        c.anotar(cmd.observaciones());
        // Releído con lock de fila dentro de la transacción: una baja que se cuele entre la lectura de arriba y aquí no deja pasar la nota,
        // y el acumulado de NC se lee con el documento bloqueado, así que dos NC concurrentes no pueden acreditarlo dos veces.
        return emitir(tenant, c, cmd.correlativo(), cmd.enviarAutomatico(), () -> {
            Comprobante bloqueado = exigirModificable(comprobantes.bloquearPorNumero(tenantId, tipoAfectado, afectado.serie(), afectado.numero()), tipoAfectado, afectado.serie(), afectado.numero());
            exigirSinBajaEnCurso(tenantId, bloqueado);
            if (cmd.tipo() == TipoDocumento.NOTA_CREDITO) exigirQueNoSupereAlDocumento(c.totales(), nota.motivo(), bloqueado, acreditadoPorNotas(tenantId, bloqueado));
        }, enAlcance);
    }

    /** «factura» o «boleta»: cómo nombran los mensajes al documento que modifica una nota. */
    private static String nombre(TipoDocumento tipo) { return tipo == TipoDocumento.BOLETA ? "boleta" : "factura"; }

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
    private record Acreditado(BigDecimal total, BigDecimal gravado, BigDecimal igv, BigDecimal ivap, BigDecimal exonerado, BigDecimal inafecto, BigDecimal gratuito, BigDecimal igvGratuitas, BigDecimal exportacion) {
        static final Acreditado CERO = new Acreditado(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Acreditado mas(Totales t) {
            return new Acreditado(total.add(t.total()), gravado.add(t.gravado()), igv.add(t.igv()), ivap.add(t.ivap()), exonerado.add(t.exonerado()), inafecto.add(t.inafecto()), gratuito.add(t.gratuito()), igvGratuitas.add(t.igvGratuitas()), exportacion.add(t.exportacion()));
        }
    }

    /**
     * Una baja en curso (GENERADA/ENVIADA/ERROR_ENVIO) sobre la factura: si SUNAT la acepta, la nota caería sobre un documento
     * anulado (2120). El estado del documento sigue ACEPTADO mientras tanto, así que `exigirModificable` no lo ve; el portal
     * ya lo bloqueaba (`admiteNotas`) pero la API quedaba abierta.
     */
    private void exigirSinBajaEnCurso(UUID tenantId, Comprobante afectado) {
        bajas.deComprobante(tenantId, afectado.id()).stream().filter(ComunicacionBaja::pendiente).findFirst().ifPresent(b -> {
            throw new DomainException("NOTA_INVALIDA", "2120 - La " + nombre(afectado.tipo()) + " " + afectado.serie() + "-" + afectado.numero() + " tiene una baja en curso (" + b.identificador()
                    + ", " + b.estado() + "): si SUNAT la acepta, la nota caería sobre un documento anulado");
        });
    }

    /** La factura o boleta serie-número debe existir en la empresa, estar aceptada por SUNAT y no anulada (2119/2120; en la boleta, 2989/2990). */
    private static Comprobante exigirModificable(Optional<Comprobante> buscado, TipoDocumento tipo, String serie, long numero) {
        String documento = "La " + nombre(tipo) + " " + serie + "-" + numero;
        Comprobante afectado = buscado.orElseThrow(() -> new DomainException("NOTA_INVALIDA", "2119 - " + documento + " no existe en esta empresa"));
        if (afectado.estado() == EstadoDocumento.ANULADO)
            throw new DomainException("NOTA_INVALIDA", "2120 - " + documento + " está anulada");
        if (afectado.estado() != EstadoDocumento.ACEPTADO && afectado.estado() != EstadoDocumento.ACEPTADO_CON_OBS)
            throw new DomainException("NOTA_INVALIDA", "2119 - " + documento + " no está aceptada por SUNAT (estado " + afectado.estado() + ")");
        return afectado;
    }

    /**
     * #405: arma y valida el comprobante con las reglas de dominio, sin numerarlo, guardarlo ni enviarlo. Lo comparten la emisión y {@link #calcularFactura}: lo que se
     * calcula es exactamente lo que se emitiría.
     */
    private Comprobante armarFactura(Tenant tenant, UUID tenantId, EmitirFacturaCommand cmd, List<Anticipo> anticipos) {
        // La cuenta de detracciones puede omitirse en la factura si la empresa la tiene configurada.
        Detraccion detraccion = cmd.detraccion() != null && cmd.detraccion().sinCuenta() ? cmd.detraccion().conCuenta(tenant.cuentaDetracciones()) : cmd.detraccion();

        // #20: la letra de la serie dice qué se emite (F factura, B boleta), como en SUNAT; los dos se numeran, firman y envían igual.
        boolean boleta = cmd.serie() != null && cmd.serie().startsWith("B");
        Comprobante c = (boleta ? Comprobante.boleta(tenantId, cmd.serie(), cmd.fechaEmision(), cmd.moneda(), cmd.tipoOperacion(), cmd.receptor(), cmd.items())
                        : Comprobante.factura(tenantId, cmd.serie(), cmd.fechaEmision(), cmd.moneda(), cmd.tipoOperacion(), cmd.receptor(), cmd.items()))
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
                .tipoCambio(cmd.tipoCambio())
                .crear(clock);
        c.anotar(cmd.observaciones());
        return c;
    }

    @Override
    public Comprobante calcularFactura(UUID tenantId, EmitirFacturaCommand cmd) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Empresa no encontrada"));
        return armarFactura(tenant, tenantId, cmd, cmd.anticipos() == null ? List.of() : cmd.anticipos());
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
     * los importes del documento que modifica. Sobre facturas (F/E) el 3286 es estricto (fila 111: «mayor a la sumatoria»,
     * sin tolerancia); sobre boletas admite +1 (fila 113, #293: SUNAT lo vuelve error desde el 2027-01-01 y khipu lo aplica ya); el 3503
     * admite +1 en cada concepto en los dos (filas 114–122). Las reglas eximen al motivo 10 «Otros conceptos». SUNAT lo comprueba nota por nota;
     * khipu descuenta además lo ya acreditado por las NC anteriores, para que varias notas no sumen más que el documento (#83).
     */
    private static void exigirQueNoSupereAlDocumento(Totales nc, String motivo, Comprobante afectado, Acreditado previo) {
        Totales f = afectado.totales();
        record Limite(String concepto, String regla, BigDecimal nota, BigDecimal factura, BigDecimal acreditado, BigDecimal tolerancia) {}
        // El motivo 10 «Otros conceptos» está exento de los nueve límites: las filas 111/113 (3286) y 114–122 (3503) empiezan
        // todas con «diferente de '10'». La primera corrección eximió solo el 3286 y una NC 10 legal caía en 3503.
        if ("10".equals(motivo)) return;
        List<Limite> limites = new java.util.ArrayList<>();
        limites.add(new Limite("importe total", "3286", nc.total(), f.total(), previo.total(), afectado.tipo() == TipoDocumento.BOLETA ? BigDecimal.ONE : BigDecimal.ZERO));
        BigDecimal tol = BigDecimal.ONE;
        limites.addAll(List.of(
                new Limite("valor de venta gravado", "3503", nc.gravado(), f.gravado(), previo.gravado(), tol),
                new Limite("IGV", "3503", nc.igv(), f.igv(), previo.igv(), tol),
                // Redundante con el 3286 en una factura IVAP (total = base × 1.04), se deja por fidelidad a las filas 120/122.
                new Limite("IVAP", "3503", nc.ivap(), f.ivap(), previo.ivap(), tol),
                new Limite("valor de venta exonerado", "3503", nc.exonerado(), f.exonerado(), previo.exonerado(), tol),
                new Limite("valor de venta inafecto", "3503", nc.inafecto(), f.inafecto(), previo.inafecto(), tol),
                new Limite("valor de las operaciones gratuitas", "3503", nc.gratuito(), f.gratuito(), previo.gratuito(), tol),
                // f118: el impuesto de las gratuitas (9996) por separado de su base (f117). No es redundante: una nota que cambie
                // la clase de gratuita (21 o 31–37, impuesto 0 → 11–16, gravadas) tiene la misma base y 18 % más de impuesto.
                new Limite("IGV de las operaciones gratuitas", "3503", nc.igvGratuitas(), f.igvGratuitas(), previo.igvGratuitas(), tol),
                new Limite("valor de venta de exportación", "3503", nc.exportacion(), f.exportacion(), previo.exportacion(), tol)));
        for (Limite l : limites) {
            if (l.nota().add(l.acreditado()).subtract(l.factura()).compareTo(l.tolerancia()) > 0)
                throw new DomainException("NOTA_INVALIDA", l.regla() + " - El " + l.concepto() + " de la nota (" + l.nota() + ") supera el de la " + nombre(afectado.tipo()) + " "
                        + afectado.serie() + "-" + afectado.numero() + " (" + l.factura() + ")"
                        + (l.acreditado().signum() > 0 ? ": ya acreditado " + l.acreditado() + " en otras notas de crédito" : ""));
        }
    }

    /**
     * Numera, genera, firma, valida, guarda y (si procede) envía; {@code enTransaccion} corre con el número ya asignado y la serie bloqueada.
     * <p>
     * Con clave de idempotencia (#115), lo primero de la transacción es reservarla: si se revierte, la clave queda libre para el
     * reintento; si otro pedido con la misma clave está en curso, la reserva espera a que termine, así que dos pedidos simultáneos no
     * emiten dos facturas. Un pedido ya hecho no toma la serie ni consume número, y no se reenvía a SUNAT: devuelve el comprobante de
     * entonces en su estado actual.
     */
    private Emision emitir(Tenant tenant, Comprobante c, Long correlativo, boolean enviarAutomatico, Runnable enTransaccion, ClaveEnAlcance clave) {
        UUID tenantId = tenant.id();
        // #378: antes de numerar y de reservar la clave, así un RUC que SUNAT rechazaría (1083) no gasta correlativo. Un reintento ya emitido volvió antes, en yaEmitidaSiLaHay.
        comprador.exigirQueExista(c.receptor());
        Emision emision = uow.ejecutar(() -> {
            if (clave != null) {
                Optional<IdempotenciaRepository.Registro> previo = idempotencia.reservar(clave.alcance(), clave.clave().clave(), clave.clave().huella());
                if (previo.isPresent()) return yaEmitida(tenantId, clave, previo.get());
            }
            // #18: antes de numerar, para que un rechazo no gaste correlativo; y dentro de la transacción, con la cuenta bloqueada hasta que se guarda el
            // comprobante, para que dos emisiones simultáneas no tomen las dos el último lugar libre. Un reintento con la misma clave ya volvió arriba.
            tope.exigirDisponible(tenantId, c.fechaEmision());
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
            String xml = ubl.generar(c, EmisorDeSerie.paraEmitir(emisorDeSerie, tenant, c));
            FirmaResultado firma = signer.firmar(xml, tenant.certificado());
            xsd.validar(firma.xmlFirmado(), c.tipo());

            String key = tenantId + "/" + c.fechaEmision().getYear() + "/" + String.format("%02d", c.fechaEmision().getMonthValue())
                    + "/" + c.nombreArchivo() + ".xml";
            storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
            c.firmar(firma.hash(), key);
            comprobantes.guardar(c);
            if (clave != null) idempotencia.completar(clave.alcance(), clave.clave().clave(), c.id());
            return new Emision(c, false);
        });

        if (emision.repetida() || !enviarAutomatico) return emision;
        // EnviarDocumentoService persiste el resultado y, si queda en ERROR_ENVIO, programa el reintento
        // en el outbox dentro de la misma transacción.
        return new Emision(enviar.enviar(tenantId, emision.comprobante().id()), false);
    }

    /** La misma clave con otro contenido es un error del cliente: no se reutiliza una clave para otra factura (u otra nota). */
    private Emision yaEmitida(UUID tenantId, ClaveEnAlcance clave, IdempotenciaRepository.Registro previo) {
        if (!previo.huella().equals(clave.clave().huella()))
            throw new DomainException("IDEMPOTENCIA_INVALIDA", "La clave de idempotencia " + clave.clave().clave()
                    + " ya se usó con otro contenido: genere una clave nueva para otra " + clave.documento());
        Comprobante emitida = comprobantes.buscar(tenantId, previo.recursoId())
                .orElseThrow(() -> new IllegalStateException("La clave " + clave.clave().clave() + " apunta a un comprobante que no existe"));
        return new Emision(emitida, true);
    }

    /**
     * La factura o boleta de anticipo (la serie F o B decide cuál, #295) debe ser de esta empresa y estar aceptada por SUNAT (regla 3218), al mismo
     * cliente y en la misma moneda (2071), y el monto que se regulariza —sumado a lo ya regularizado en otros comprobantes finales— no puede superar lo
     * que aquel facturó en esa afectación: SUNAT no cruza anticipos entre comprobantes, así que un doble descuento pasaría inadvertido.
     */
    private void validarFacturaDeAnticipo(UUID tenantId, EmitirFacturaCommand cmd, Anticipo a) {
        String que = a.esDeBoleta() ? "La boleta" : "La factura";
        Comprobante origen = comprobantes.bloquearPorNumero(tenantId, a.esDeBoleta() ? TipoDocumento.BOLETA : TipoDocumento.FACTURA, a.serie(), a.numero())
                .orElseThrow(() -> new DomainException("ANTICIPO_INVALIDO", "3218 - " + que + " de anticipo " + a.comprobante() + " no existe en esta empresa"));
        if (origen.estado() != EstadoDocumento.ACEPTADO && origen.estado() != EstadoDocumento.ACEPTADO_CON_OBS)
            throw new DomainException("ANTICIPO_INVALIDO", "3218 - " + que + " de anticipo " + a.comprobante() + " no está aceptada por SUNAT (estado " + origen.estado() + ")");
        if (!origen.moneda().equals(cmd.moneda()))
            throw new DomainException("ANTICIPO_INVALIDO", "2071 - " + que + " de anticipo " + a.comprobante() + " es en " + origen.moneda() + " y este comprobante en " + cmd.moneda());
        if (!origen.receptor().numDoc().equals(cmd.receptor().numDoc()))
            throw new DomainException("ANTICIPO_INVALIDO", que + " de anticipo " + a.comprobante() + " fue emitida a otro cliente (documento " + origen.receptor().numDoc() + ")");
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
