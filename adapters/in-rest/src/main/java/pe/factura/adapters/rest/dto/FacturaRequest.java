package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Cargo;
import pe.factura.domain.documento.CodigoProductoSunat;
import pe.factura.domain.documento.Descuento;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.Percepcion;
import pe.factura.domain.documento.RetencionIgv;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Gtin;
import pe.factura.domain.documento.Isc;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;
import pe.factura.domain.documento.GuiaRelacionada;
import pe.factura.domain.documento.DocumentoRelacionado;
import pe.factura.domain.documento.TipoAfectacionIgv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaRequest(
        @NotBlank @Pattern(regexp = "F[A-Z0-9]{3}", message = "serie de factura inválida") @Schema(example = "F001", description = "Serie de factura: `F` + 3 alfanuméricos, registrada previamente en `POST /v1/series`") String serie,
        @Positive @Schema(example = "125", description = "Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`") Long correlativo,
        @NotNull @Schema(example = "2026-09-14", description = "Fecha de emisión (`YYYY-MM-DD`), no futura. SUNAT exige recibir la factura dentro de los 3 días calendario siguientes") LocalDate fechaEmision,
        @Schema(example = "2026-10-14", description = "Fecha de vencimiento del pago (`cbc:DueDate`), no anterior a la de emisión. Informativa: al crédito las cuotas de `forma_pago` siguen siendo obligatorias. Opcional") LocalDate fechaVencimiento,
        @Pattern(regexp = "\\d{4}") @Schema(example = "0101", description = "Tipo de operación, catálogo 51 (`GET /v1/catalogos/51`); un código fuera del catálogo responde `422 TIPO_OPERACION_INVALIDO` (regla 3206). `0101` venta interna (por defecto), `1001` operación sujeta a detracción. Exportación (`0200`) aún no soportada") String tipoOperacion,
        @NotBlank @Pattern(regexp = "PEN|USD|EUR") @Schema(example = "PEN", description = "Moneda ISO 4217 de todo el comprobante: `PEN`, `USD` o `EUR` (catálogo 02)") String moneda,
        @NotNull @Valid ClienteDto cliente,
        @NotEmpty @Valid List<ItemDto> items,
        @Valid @Schema(description = "Forma de pago (RS 193-2020). Si se omite, al contado.") FormaPagoDto formaPago,
        @Valid @Schema(description = "Descuento sobre el total (catálogo 53: `02` si afecta la base del IGV —requiere ítems gravados—, `03` si no). Opcional.") DescuentoDto descuentoGlobal,
        @Valid @Schema(description = "Cargos sobre el total (catálogo 53): `afecta_base_igv: true` → `49` (se suma a la base gravada; requiere ítems gravados), `false` → `50`, `motivo: recargo_consumo` → `46`. Los que no afectan la base suman al importe a pagar (`ChargeTotalAmount`). Opcional.") List<CargoDto> cargos,
        @Valid @Schema(description = "Detracción (SPOT). Obligatoria cuando `tipo_operacion` es 1001–1004 y prohibida en los demás casos.") DetraccionDto detraccion,
        @Valid @Schema(description = "Retención del IGV que aplicará el cliente por ser agente de retención (catálogo 53: 62). Informativa; no cambia los totales.") RetencionDto retencionIgv,
        @Valid @Schema(description = "Percepción del IGV que cobra la empresa por ser agente de percepción (catálogo 53: 51/52/53). Solo con `tipo_operacion` 2001, al contado y en PEN.") PercepcionDto percepcion,
        @Valid @Schema(description = "Facturas de anticipo que se regularizan en esta factura. Los ítems describen la operación completa y khipu descuenta cada anticipo de la base (código 04/05/06) y del importe a pagar.") List<AnticipoDto> anticipos,
        @Size(min = 1, max = 20) @Schema(example = "OC-2026-0457", description = "Número de la orden de compra o de servicio del cliente (1–20 caracteres, sin saltos de línea): `cac:OrderReference`. Opcional, informativo (regla 4233)") String ordenCompra,
        @Valid @Schema(description = "Guías de remisión que sustentan el traslado (`cac:DespatchDocumentReference`). Opcional.") List<GuiaDto> guias,
        @Valid @Schema(description = "Otros documentos relacionados con la operación (`cac:AdditionalDocumentReference`, catálogo 12). Opcional; las facturas de anticipo van en `anticipos`.") List<DocumentoRelacionadoDto> documentosRelacionados,
        @Schema(example = "-0.40", description = "Redondeo del importe total (`PayableRoundingAmount`): se suma al total a pagar; entre −1.00 y 1.00 con 2 decimales (regla 3303). Útil para cobrar en efectivo sin céntimos. Opcional") BigDecimal redondeo,
        @Schema(example = "true", description = "`true` (por defecto) envía a SUNAT en la misma llamada; `false` deja el comprobante `FIRMADO` para enviarlo luego con `POST /v1/facturas/{id}/enviar` (p. ej. para emitir en lote y enviar después)") Boolean enviarAutomatico,
        @Size(max = 1000) @Schema(example = "Entrega en almacén central. Horario: 9 a 18 h.", description = "Texto libre que se imprime en el bloque «Observaciones» del PDF (hasta 1000 caracteres, admite saltos de línea). No va al XML ni a SUNAT. Si se omite, se imprimen las observaciones por defecto de la empresa") String observaciones,
        @Schema(example = "[\"2001\"]", description = "Leyendas del catálogo 52 que declara el emisor (`GET /v1/catalogos/52`): `2001` bienes en Amazonía, `2002` servicios en Amazonía, `2003` contratos de construcción en Amazonía, `2004` paquete turístico, `2005` venta itinerante, `2008`/`2009` zona comercial de Tacna… Van al XML como `cbc:Note`. 2001/2002/2003/2008 exigen total exonerado mayor a 0 (3283–3285, 3289). Las automáticas (monto en letras, gratuitas, detracción, percepción, IVAP) las pone khipu: `422 LEYENDA_INVALIDA` si se envían. Opcional") List<String> leyendas) {

    public record ClienteDto(
            @NotBlank @Schema(example = "6", description = "Tipo de documento de identidad, catálogo 06. En factura debe ser `6` (RUC); `1` DNI, `4` carné de extranjería y `7` pasaporte se usan en boletas") String tipoDoc,
            @NotBlank @Schema(example = "20123456786", description = "RUC de 11 dígitos del adquirente") String numDoc,
            @NotBlank @Schema(example = "Comercial Andina SAC", description = "Razón social tal como figura en la ficha RUC del adquirente") String razonSocial,
            @Schema(example = "Av. Javier Prado Este 123, San Isidro", description = "Dirección del adquirente (opcional; se imprime en el XML)") String direccion) {}

    public record ItemDto(
            @Schema(example = "SKU-001", description = "Código interno del producto o servicio (opcional)") String codigo,
            @NotBlank @Schema(example = "Servicio de consultoría", description = "Descripción detallada del bien o servicio") String descripcion,
            @NotBlank @Schema(example = "NIU", description = "Unidad de medida UN/ECE rec 20 (catálogo 03, `GET /v1/catalogos/03`, lista las más usadas): `NIU` unidad (bienes), `ZZ` unidad (servicios), `KGM` kilogramo, `HUR` hora… khipu no la valida contra el catálogo: un código inexistente lo rechaza SUNAT") String unidad,
            @NotNull @Positive @Schema(example = "2", description = "Cantidad, hasta 10 decimales") BigDecimal cantidad,
            @NotNull @PositiveOrZero @Schema(example = "1000.00", description = "Precio de venta unitario **con IGV incluido** (gravados); khipu calcula el valor unitario sin IGV") BigDecimal precioUnitario,
            @NotBlank @Pattern(regexp = "1[0-6]|2[01]|3[0-7]", message = "afectación IGV no soportada: use 10–16, 20, 21 o 30–37 (catálogo 07)")
            @Schema(example = "10", description = """
                    Afectación del IGV, catálogo 07 (`GET /v1/catalogos/07`). Onerosas: `10` gravado (IGV 18 %, o la tasa reducida del padrón de restaurantes y hoteles si la empresa la activó; precio con IGV),
                    `20` exonerado (Apéndice I de la Ley del IGV), `30` inafecto (fuera del ámbito). Gratuitas (bonificaciones, muestras,
                    retiros): `11`–`16` gravadas, `21` exonerada, `31`–`37` inafectas — el `precio_unitario` es el **valor referencial sin
                    IGV**, la línea no suma al importe a pagar y su IGV solo se informa (tributo 9996). No soportadas: `17` (IVAP) y `40` (exportación).""") String tipoAfectacionIgv,
            @Valid @Schema(description = "Descuento de la línea (catálogo 53: `00` si afecta la base del IGV, `01` si no). Opcional.") DescuentoDto descuento,
            @Valid @Schema(description = "Cargos de la línea (catálogo 53): `afecta_base_igv: true` → `47` (se suma al valor de venta y paga IGV), `false` → `48` (se cobra sin IGV). No admitidos en gratuitas. Opcional.") List<CargoDto> cargos,
            @Valid @Schema(description = "Impuesto Selectivo al Consumo del ítem (bebidas alcohólicas, combustibles, vehículos…). Opcional; el `precio_unitario` lo incluye.") IscDto isc,
            @Schema(example = "false", description = "`true` si el ítem son bolsas de plástico afectas al ICBPER: una bolsa por unidad (`unidad` NIU), monto fijo vigente por año incluido en `precio_unitario`") Boolean icbper,
            @Pattern(regexp = "[0-9]{8}", message = "código de producto SUNAT de 8 dígitos (UNSPSC, catálogo 25)") @Schema(example = "15101505", description = "Código de producto SUNAT (catálogo 25, UNSPSC de 8 dígitos; `GET /v1/catalogos/25` lista los que SUNAT exige a los padrones obligados, detracciones y percepciones). Opcional; obligatorio para los emisores del padrón (regla 4331). SUNAT observa los que no llegan al tercer nivel (terminados en 0000, regla 4337)") String codigoSunat,
            @Valid @Schema(description = "Código GTIN (GS1) del producto. Opcional") GtinDto gtin) {

        Item aDominio() {
            return new Item(codigo, descripcion, unidad, cantidad, precioUnitario, TipoAfectacionIgv.porCodigo(tipoAfectacionIgv),
                    descuento == null ? null : descuento.aDominio(), isc == null ? null : isc.aDominio(), Boolean.TRUE.equals(icbper),
                    FacturaRequest.cargos(this.cargos, false), CodigoProductoSunat.de(codigoSunat), gtin == null ? null : gtin.aDominio());
        }
    }

    public record GtinDto(
            @NotBlank @Pattern(regexp = "GTIN-(8|12|13|14)", message = "tipo de GTIN: GTIN-8, GTIN-12, GTIN-13 o GTIN-14") @Schema(example = "GTIN-13", description = "Estructura GS1: `GTIN-8`, `GTIN-12`, `GTIN-13` o `GTIN-14` (regla 4335)") String tipo,
            @NotBlank @Schema(example = "7750182000123", description = "Dígitos del código; la longitud debe coincidir con el tipo (regla 4334)") String codigo) {
        Gtin aDominio() { return new Gtin(tipo, codigo); }
    }

    /** ISC: sistema del catálogo 08; `tasa` (%) para 01 al valor y 03 al PVP (con `base_pvp`), `monto_unitario` para 02 monto fijo. */
    public record IscDto(
            @NotBlank @Pattern(regexp = "0[123]", message = "sistema de ISC: 01 (al valor), 02 (monto fijo) o 03 (precio de venta al público)") @Schema(example = "01", description = "`01` al valor (tasa sobre el valor de venta), `02` monto fijo por unidad, `03` al valor según precio de venta al público (tasa sobre `base_pvp`, el PVP sugerido unitario; cervezas, cigarrillos, gaseosas) — catálogo 08") String sistema,
            @Schema(example = "35", description = "Tasa en %, hasta 5 decimales (sistemas 01 y 03)") BigDecimal tasa,
            @Schema(example = "2.25", description = "Importe por unidad (sistema 02), hasta 5 decimales") BigDecimal montoUnitario,
            @Schema(example = "3.50", description = "Solo sistema 03: precio de venta al público sugerido por unidad, sin IGV (base del ISC en el XML, regla 3108); no puede ser menor que el valor unitario. Hasta 5 decimales") BigDecimal basePvp) {
        Isc aDominio() { return new Isc(sistema, tasa, montoUnitario, basePvp); }
    }

    /** Un descuento se expresa como porcentaje **o** como monto (sobre el valor de venta sin IGV), nunca ambos. */
    public record DescuentoDto(
            @Schema(example = "10", description = "Porcentaje sobre el valor de venta sin IGV (hasta 5 decimales, menor que 100)") BigDecimal porcentaje,
            @Schema(example = "50.00", description = "Monto fijo sin IGV (hasta 2 decimales, menor que la base)") BigDecimal monto,
            @Schema(example = "true", description = "`true` (por defecto): reduce la base imponible y por tanto el IGV. `false`: reduce solo lo que se paga (descuento financiero)") Boolean afectaBaseIgv) {

        Descuento aDominio() {
            if ((porcentaje == null) == (monto == null))
                throw new DomainException("DESCUENTO_INVALIDO", "Indique porcentaje o monto del descuento, no ambos");
            boolean afecta = afectaBaseIgv == null || afectaBaseIgv;
            return porcentaje != null ? Descuento.porcentaje(porcentaje, afecta) : Descuento.monto(monto, afecta);
        }
    }

    /**
     * Un cargo se expresa como porcentaje **o** como monto (sobre el valor de venta sin IGV), nunca ambos, y decide si afecta la
     * base del IGV igual que un descuento. El código del catálogo 53 lo deriva el dominio ({@link Cargo#deLinea}, {@link Cargo#global}).
     */
    public record CargoDto(
            @Schema(example = "10", description = "Porcentaje sobre el valor de venta sin IGV (hasta 5 decimales, menor que 1000)") BigDecimal porcentaje,
            @Schema(example = "25.00", description = "Monto fijo sin IGV (hasta 2 decimales)") BigDecimal monto,
            @Schema(example = "true", description = "`true` (por defecto): se suma a la base imponible y paga IGV (flete, embalaje) → código 47 por línea, 49 global. `false`: se cobra sin IGV (reembolso de gastos) → 48 por línea, 50 global") Boolean afectaBaseIgv,
            @Schema(example = "recargo_consumo", description = "Solo global: `recargo_consumo` para el recargo al consumo y/o propinas (código 46, sin IGV por la Ley 25988); no admite `afecta_base_igv: true`. Un motivo desconocido responde `422 CARGO_INVALIDO`") String motivo) {

        Cargo aDominio(boolean global) {
            if ((porcentaje == null) == (monto == null))
                throw new DomainException("CARGO_INVALIDO", "Indique porcentaje o monto del cargo, no ambos");
            Cargo.Motivo m = motivo == null ? null : Cargo.Motivo.porNombre(motivo);
            if (m != null && !global)
                throw new DomainException("CARGO_INVALIDO", "4268 - El recargo al consumo (46) es un cargo global, no de línea");
            // Por defecto afecta la base (como los descuentos), salvo cuando hay motivo: el recargo al consumo nunca la afecta.
            boolean afecta = afectaBaseIgv == null ? m == null : afectaBaseIgv;
            Cargo.Tipo tipo = porcentaje != null ? Cargo.Tipo.PORCENTAJE : Cargo.Tipo.MONTO;
            BigDecimal valor = porcentaje != null ? porcentaje : monto;
            return global ? Cargo.global(afecta, m, tipo, valor) : Cargo.deLinea(afecta, tipo, valor);
        }
    }

    public record FormaPagoDto(
            @NotBlank @Pattern(regexp = "contado|credito") @Schema(example = "credito", description = "`contado`: pago único al emitir. `credito`: pago diferido; exige `monto_pendiente` y al menos una cuota (RS 193-2020)") String tipo,
            @Schema(example = "1180.00", description = "Monto neto pendiente de pago; obligatorio al crédito. Formato e importes los valida el dominio con el código SUNAT (3250, 3265, 3319)") BigDecimal montoPendiente,
            @Valid @Schema(description = "Cuotas; obligatorias al crédito y deben sumar el monto pendiente") List<CuotaDto> cuotas) {

        public record CuotaDto(
                @Schema(example = "590.00", description = "Positivo, hasta 2 decimales (SUNAT 3253)") BigDecimal monto,
                @Schema(example = "2026-10-15", description = "Posterior a la fecha de emisión (SUNAT 3256, 3267)") LocalDate vencimiento) {}

        FormaPago aDominio() {
            List<FormaPago.Cuota> cs = cuotas == null ? List.of() : cuotas.stream().map(q -> new FormaPago.Cuota(q.monto(), q.vencimiento())).toList();
            return "credito".equals(tipo) ? FormaPago.credito(montoPendiente, cs) : new FormaPago(FormaPago.Tipo.CONTADO, montoPendiente, cs);
        }
    }

    /** Datos del SPOT: el monto se deposita siempre en soles; para facturas en PEN puede omitirse y el dominio lo calcula (total × %, redondeado al sol). */
    public record DetraccionDto(
            @NotBlank @Schema(example = "022", description = "Bien o servicio sujeto a detracción, catálogo 54 (`GET /v1/catalogos/54`). Con tipo de operación 1002/1003/1004 debe ser 004/028/027") String codigoBienServicio,
            @NotNull @Schema(example = "12", description = "Porcentaje de detracción que corresponde al bien/servicio (hasta 5 decimales)") BigDecimal porcentaje,
            @Schema(example = "1416.00", description = "Monto a depositar **en soles**. Opcional en facturas en PEN (khipu lo calcula); obligatorio en otras monedas") BigDecimal monto,
            @Schema(example = "00-000-123456", description = "Número de cuenta de detracciones del emisor en el Banco de la Nación. Opcional si la empresa la tiene configurada (`PUT /v1/empresa/datos-fiscales`)") String cuentaBancoNacion,
            @Schema(example = "001", description = "Medio de pago, catálogo 59; por defecto `001` depósito en cuenta") String medioPago) {

        Detraccion aDominio() { return new Detraccion(codigoBienServicio, porcentaje, monto, cuentaBancoNacion, medioPago); }
    }

    public record RetencionDto(
            @Schema(example = "3", description = "Porcentaje de retención; por defecto la tasa legal 3 %") BigDecimal porcentaje,
            @Schema(example = "354.00", description = "Importe retenido; opcional: khipu lo calcula sobre el importe total (tolerancia SUNAT ±1)") BigDecimal monto) {
        RetencionIgv aDominio() { return new RetencionIgv(porcentaje, monto); }
    }

    public record PercepcionDto(
            @NotBlank @Pattern(regexp = "5[123]") @Schema(example = "51", description = "Régimen (catálogo 53): `51` venta interna 2 %, `52` combustible 1 %, `53` tasa especial 0,5 %") String regimen,
            @Schema(example = "2", description = "Tasa; opcional, la fija el régimen (catálogo 22)") BigDecimal porcentaje,
            @Schema(example = "1180.00", description = "Base de la percepción; por defecto el importe total") BigDecimal base,
            @Schema(example = "23.60", description = "Monto; opcional: khipu lo calcula (base × tasa, tolerancia ±1)") BigDecimal monto) {
        Percepcion aDominio() { return new Percepcion(regimen, porcentaje, base, monto); }
    }

    /**
     * Anticipo ya facturado (y aceptado por SUNAT) por esta misma empresa al mismo cliente. `monto` es el valor sin IGV; khipu
     * calcula el importe pagado (con IGV) y lo resta del total.
     */
    public record AnticipoDto(
            @NotBlank @Pattern(regexp = "F[A-Z0-9]{3}", message = "serie de la factura de anticipo inválida") @Schema(example = "F001", description = "Serie de la factura de anticipo") String serie,
            @NotNull @Positive @Schema(example = "120", description = "Número de la factura de anticipo, emitida por esta empresa y ACEPTADA por SUNAT") Long numero,
            @NotNull @Schema(example = "1000.00", description = "Valor sin IGV que se regulariza; no puede superar lo facturado en el anticipo ni lo facturado en esta factura para la misma afectación") BigDecimal monto,
            @Pattern(regexp = "gravado|exonerado|inafecto") @Schema(example = "gravado", description = "Afectación del anticipo (catálogo 53): `gravado` (04, por defecto), `exonerado` (05) o `inafecto` (06). Decide de qué base se descuenta") String afectacion,
            @Schema(example = "2026-09-01", description = "Fecha en que se pagó el anticipo (opcional, informativa)") LocalDate fechaPago) {
        Anticipo aDominio() {
            return new Anticipo(serie, numero, monto, afectacion == null ? null : Anticipo.Afectacion.valueOf(afectacion.toUpperCase()), fechaPago);
        }
    }

    public record GuiaDto(
            @NotBlank @Pattern(regexp = "09|31", message = "tipo de guía no válido: 09 (remitente) o 31 (transportista)") @Schema(example = "09", description = "`09` guía de remisión remitente, `31` guía de remisión transportista (catálogo 01)") String tipo,
            @NotBlank @Schema(example = "T001-123", description = "Serie-número de la guía: electrónica `T001-123`/`V001-45`, física `0001-123` o `EG01-45` (regla 4006)") String numero) {
        GuiaRelacionada aDominio() { return new GuiaRelacionada(tipo, numero); }
    }

    public record DocumentoRelacionadoDto(
            @NotBlank @Pattern(regexp = "0[4-9]|99", message = "tipo de documento relacionado no válido: 04–09 o 99 (catálogo 12)") @Schema(example = "05", description = "Catálogo 12: `04` ticket de salida ENAPU, `05` código SCOP, `06` factura electrónica remitente, `07` guía de remisión remitente, `08` declaración de salida del depósito franco, `09` declaración simplificada de importación, `99` otros") String tipo,
            @NotBlank @Size(max = 30) @Schema(example = "SCOP-8841203", description = "Número del documento, hasta 30 caracteres sin espacios (regla 4010)") String numero) {
        DocumentoRelacionado aDominio() { return new DocumentoRelacionado(tipo, numero); }
    }

    public EmitirFacturaCommand aComando() {
        return new EmitirFacturaCommand(serie, correlativo, fechaEmision, fechaVencimiento, moneda, tipoOperacion,
                new Receptor(cliente.tipoDoc(), cliente.numDoc(), cliente.razonSocial(), cliente.direccion()),
                items.stream().map(ItemDto::aDominio).toList(),
                formaPago == null ? FormaPago.contado() : formaPago.aDominio(),
                descuentoGlobal == null ? null : descuentoGlobal.aDominio(),
                cargos(cargos, true),
                detraccion == null ? null : detraccion.aDominio(),
                retencionIgv == null ? null : retencionIgv.aDominio(),
                percepcion == null ? null : percepcion.aDominio(),
                anticipos == null ? List.of() : anticipos.stream().map(AnticipoDto::aDominio).toList(),
                new Referencias(ordenCompra, guias == null ? List.of() : guias.stream().map(GuiaDto::aDominio).toList(),
                        documentosRelacionados == null ? List.of() : documentosRelacionados.stream().map(DocumentoRelacionadoDto::aDominio).toList()),
                redondeo,
                enviarAutomatico == null || enviarAutomatico, observaciones, leyendas == null ? List.of() : leyendas);
    }

    static List<Cargo> cargos(List<CargoDto> dtos, boolean globales) {
        return dtos == null ? List.of() : dtos.stream().map(d -> d.aDominio(globales)).toList();
    }
}
