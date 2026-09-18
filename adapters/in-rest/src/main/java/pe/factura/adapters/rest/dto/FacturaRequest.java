package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Cargo;
import pe.factura.domain.documento.Descuento;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.Percepcion;
import pe.factura.domain.documento.RetencionIgv;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Isc;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaRequest(
        @NotBlank @Pattern(regexp = "F[A-Z0-9]{3}", message = "serie de factura inválida") @Schema(example = "F001", description = "Serie de factura: `F` + 3 alfanuméricos, registrada previamente en `POST /v1/series`") String serie,
        @Positive @Schema(example = "125", description = "Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`") Long correlativo,
        @NotNull @Schema(example = "2026-09-14", description = "Fecha de emisión (`YYYY-MM-DD`), no futura. SUNAT exige recibir la factura dentro de los 3 días calendario siguientes") LocalDate fechaEmision,
        @Pattern(regexp = "\\d{4}") @Schema(example = "0101", description = "Tipo de operación, catálogo 51 (`GET /v1/catalogos/51`); un código fuera del catálogo responde `422 TIPO_OPERACION_INVALIDO` (regla 3206). `0101` venta interna (por defecto), `1001` operación sujeta a detracción. Exportación (`0200`) aún no soportada") String tipoOperacion,
        @NotBlank @Pattern(regexp = "PEN|USD|EUR") @Schema(example = "PEN", description = "Moneda ISO 4217 de todo el comprobante: `PEN`, `USD` o `EUR` (catálogo 02)") String moneda,
        @NotNull @Valid ClienteDto cliente,
        @NotEmpty @Valid List<ItemDto> items,
        @Valid @Schema(description = "Forma de pago (RS 193-2020). Si se omite, al contado.") FormaPagoDto formaPago,
        @Valid @Schema(description = "Descuento sobre el total (catálogo 53: `02` si afecta la base del IGV —requiere ítems gravados—, `03` si no). Opcional.") DescuentoDto descuentoGlobal,
        @Valid @Schema(description = "Cargos globales (catálogo 53): `49` afecta la base del IGV —requiere ítems gravados—, `50` no la afecta, `46` recargo al consumo y/o propinas. Los que no afectan la base suman al importe a pagar (`ChargeTotalAmount`). Opcional.") List<CargoDto> cargos,
        @Valid @Schema(description = "Detracción (SPOT). Obligatoria cuando `tipo_operacion` es 1001–1004 y prohibida en los demás casos.") DetraccionDto detraccion,
        @Valid @Schema(description = "Retención del IGV que aplicará el cliente por ser agente de retención (catálogo 53: 62). Informativa; no cambia los totales.") RetencionDto retencionIgv,
        @Valid @Schema(description = "Percepción del IGV que cobra la empresa por ser agente de percepción (catálogo 53: 51/52/53). Solo con `tipo_operacion` 2001, al contado y en PEN.") PercepcionDto percepcion,
        @Valid @Schema(description = "Facturas de anticipo que se regularizan en esta factura. Los ítems describen la operación completa y khipu descuenta cada anticipo de la base (código 04/05/06) y del importe a pagar.") List<AnticipoDto> anticipos,
        @Schema(example = "true", description = "`true` (por defecto) envía a SUNAT en la misma llamada; `false` deja el comprobante `FIRMADO` para enviarlo luego con `POST /v1/facturas/{id}/enviar` (p. ej. para emitir en lote y enviar después)") Boolean enviarAutomatico) {

    public record ClienteDto(
            @NotBlank @Schema(example = "6", description = "Tipo de documento de identidad, catálogo 06. En factura debe ser `6` (RUC); `1` DNI, `4` carné de extranjería y `7` pasaporte se usan en boletas") String tipoDoc,
            @NotBlank @Schema(example = "20123456789", description = "RUC de 11 dígitos del adquirente") String numDoc,
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
                    Afectación del IGV, catálogo 07 (`GET /v1/catalogos/07`). Onerosas: `10` gravado (IGV 18 %, precio con IGV),
                    `20` exonerado (Apéndice I de la Ley del IGV), `30` inafecto (fuera del ámbito). Gratuitas (bonificaciones, muestras,
                    retiros): `11`–`16` gravadas, `21` exonerada, `31`–`37` inafectas — el `precio_unitario` es el **valor referencial sin
                    IGV**, la línea no suma al importe a pagar y su IGV solo se informa (tributo 9996). No soportadas: `17` (IVAP) y `40` (exportación).""") String tipoAfectacionIgv,
            @Valid @Schema(description = "Descuento de la línea (catálogo 53: `00` si afecta la base del IGV, `01` si no). Opcional.") DescuentoDto descuento,
            @Valid @Schema(description = "Cargos de la línea (catálogo 53): `47` afecta la base del IGV (se suma al valor de venta y paga IGV), `48` no la afecta (se cobra sin IGV). No admitidos en gratuitas. Opcional.") List<CargoDto> cargos,
            @Valid @Schema(description = "Impuesto Selectivo al Consumo del ítem (bebidas alcohólicas, combustibles, vehículos…). Opcional; el `precio_unitario` lo incluye.") IscDto isc,
            @Schema(example = "false", description = "`true` si el ítem son bolsas de plástico afectas al ICBPER: una bolsa por unidad (`unidad` NIU), monto fijo vigente por año incluido en `precio_unitario`") Boolean icbper) {}

    /** ISC: sistema del catálogo 08; `tasa` (%) para 01 al valor, `monto_unitario` para 02 monto fijo. El 03 (precio de venta al público) no está soportado. */
    public record IscDto(
            @NotBlank @Pattern(regexp = "0[12]", message = "sistema de ISC no soportado: use 01 (al valor) o 02 (monto fijo); el 03 (precio de venta al público) requiere una base PVP que la API aún no recibe") @Schema(example = "01", description = "`01` al valor, `02` monto fijo por unidad (catálogo 08). `03` precio de venta al público **no soportado**: su base es el PVP sugerido, no el valor de venta") String sistema,
            @Schema(example = "35", description = "Tasa sobre el valor de venta (sistema 01), hasta 5 decimales") BigDecimal tasa,
            @Schema(example = "2.25", description = "Importe por unidad (sistema 02), hasta 5 decimales") BigDecimal montoUnitario) {
        Isc aDominio() { return new Isc(sistema, tasa, montoUnitario); }
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

    /** Un cargo se expresa como porcentaje **o** como monto (sobre el valor de venta sin IGV), nunca ambos; el código fija nivel y efecto en el IGV. */
    public record CargoDto(
            @NotBlank @Pattern(regexp = "4[6-9]|50", message = "código de cargo no válido: use 47/48 por línea o 46/49/50 globales (catálogo 53)")
            @Schema(example = "50", description = "Código del catálogo 53: línea `47` (afecta la base del IGV) / `48` (no); global `49` (afecta) / `50` (no) / `46` recargo al consumo y propinas (no)") String codigo,
            @Schema(example = "10", description = "Porcentaje sobre el valor de venta sin IGV (hasta 5 decimales)") BigDecimal porcentaje,
            @Schema(example = "25.00", description = "Monto fijo sin IGV (hasta 2 decimales)") BigDecimal monto) {

        Cargo aDominio() {
            if ((porcentaje == null) == (monto == null))
                throw new DomainException("CARGO_INVALIDO", "Indique porcentaje o monto del cargo, no ambos");
            return porcentaje != null ? Cargo.porcentaje(codigo, porcentaje) : Cargo.monto(codigo, monto);
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

    public EmitirFacturaCommand aComando() {
        return new EmitirFacturaCommand(serie, correlativo, fechaEmision, moneda, tipoOperacion,
                new Receptor(cliente.tipoDoc(), cliente.numDoc(), cliente.razonSocial(), cliente.direccion()),
                items.stream().map(i -> new Item(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), TipoAfectacionIgv.porCodigo(i.tipoAfectacionIgv()),
                        i.descuento() == null ? null : i.descuento().aDominio(), i.isc() == null ? null : i.isc().aDominio(), Boolean.TRUE.equals(i.icbper()),
                        cargos(i.cargos()))).toList(),
                formaPago == null ? FormaPago.contado() : formaPago.aDominio(),
                descuentoGlobal == null ? null : descuentoGlobal.aDominio(),
                cargos(cargos),
                detraccion == null ? null : detraccion.aDominio(),
                retencionIgv == null ? null : retencionIgv.aDominio(),
                percepcion == null ? null : percepcion.aDominio(),
                anticipos == null ? List.of() : anticipos.stream().map(AnticipoDto::aDominio).toList(),
                enviarAutomatico == null || enviarAutomatico);
    }

    private static List<Cargo> cargos(List<CargoDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(CargoDto::aDominio).toList();
    }
}
