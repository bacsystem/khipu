package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirAgenteCommand;
import pe.factura.domain.documento.ComprobanteDeAgente;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que comparten el cuerpo de {@code POST /v1/retenciones} y el de {@code POST /v1/percepciones} (#40): cada documento que el agente paga o cobra, con su pago. Lleva nombre de
 * esquema propio: springdoc nombra por nombre simple.
 */
public final class AgenteRequest {
    private AgenteRequest() {}

    @Schema(name = "DocumentoDeAgente")
    public record DocumentoDto(
            @NotBlank @Pattern(regexp = "\\d{2}") @Schema(example = "01", description = "Tipo de documento, catálogo 01: `01` factura, `08` nota de débito, `12` ticket y, solo en la percepción, `03` boleta. La nota de crédito (`07`) y otro comprobante de retención o percepción, que la hoja admite sin pago, todavía no: `422` (2692)") String tipo,
            @NotBlank @Size(max = 41) @Schema(example = "F001-123", description = "Serie y número: `F###`/`E001` electrónica, 4 dígitos física (y `B###`/`EB01` en la percepción) seguido del número de 1 a 8 dígitos; un ticket, la serie de la máquina y su número (2694)") String numero,
            @NotNull @Schema(example = "2026-10-05", description = "Fecha de emisión del documento, no posterior a la del comprobante") LocalDate fechaEmision,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) @Schema(example = "1180.00", description = "Importe total del documento, en su moneda (2696)") BigDecimal importeTotal,
            @Pattern(regexp = "[A-Z]{3}") @Schema(example = "PEN", description = "Moneda del documento (catálogo 02); por defecto `PEN`") String moneda,
            @NotNull @Valid @Schema(description = "El pago (retención) o el cobro (percepción) de este documento") PagoDto pago,
            @Valid @Schema(description = "Solo fuera de soles, obligatorio (2721): soles por unidad de `moneda` con que se convierte el pago") TipoCambioDto tipoCambio) {
        ComprobanteDeAgente.Documento aDominio() {
            return new ComprobanteDeAgente.Documento(tipo, numero, fechaEmision, importeTotal, moneda, pago.numero() == null ? 1 : pago.numero(), pago.fecha(), pago.importe(),
                    tipoCambio == null ? null : tipoCambio.factor(), tipoCambio == null ? null : tipoCambio.fecha());
        }
    }

    @Schema(name = "PagoDeDocumento")
    public record PagoDto(
            @Positive @Max(999_999_999) @Schema(example = "1", description = "Número del pago o cobro de ese documento (1 el primero, 2 el segundo de un pago en cuotas…); por defecto 1. No se repite para el mismo documento (2626)") Long numero,
            @NotNull @Schema(example = "2026-10-20", description = "Fecha del pago o cobro: desde la emisión del documento (o desde el primer día del mes del comprobante si el documento es de un mes anterior) hasta la emisión del comprobante (2625/2612)") LocalDate fecha,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) @Schema(example = "1180.00", description = "Importe pagado o cobrado **sin** la retención o la percepción, en la moneda del documento; no mayor que su importe total") BigDecimal importe) {}

    @Schema(name = "TipoCambioDeDocumento")
    public record TipoCambioDto(
            @NotNull @Positive @Digits(integer = 4, fraction = 6) @Schema(example = "3.756", description = "Soles por unidad de la moneda del documento, hasta 4 enteros y 6 decimales (2716)") BigDecimal factor,
            @Schema(example = "2026-10-20", description = "Fecha del tipo de cambio; por defecto la del pago (2722)") LocalDate fecha) {}

    static List<ComprobanteDeAgente.Documento> documentos(List<DocumentoDto> docs) { return docs.stream().map(DocumentoDto::aDominio).toList(); }

    static EmitirAgenteCommand comando(String serie, Long correlativo, LocalDate fechaEmision, Receptor contraparte, TipoDocumento tipo, String regimen, BigDecimal tasa,
                                       List<DocumentoDto> documentos, Boolean enviarAutomatico, String observaciones) {
        // Sin redondeo: e-beta no lee el XML que lo lleva (0306); ver ComprobanteDeAgente.validarRedondeo.
        return new EmitirAgenteCommand(serie, correlativo, fechaEmision, contraparte, new ComprobanteDeAgente(tipo, regimen, tasa, documentos(documentos)), null,
                enviarAutomatico == null || enviarAutomatico, observaciones);
    }

    /** Cuerpo de {@code POST /v1/retenciones}. */
    @Schema(name = "RetencionRequest")
    public record Retencion(
            @NotBlank @Pattern(regexp = "R[A-Z0-9]{3}", message = "serie de retención inválida (R + 3 alfanuméricos, p. ej. R001)") @Schema(example = "R001", description = "Serie registrada con el tipo `20`: `R` + 3 alfanuméricos (regla 1001)") String serie,
            @Positive @Schema(example = "15", description = "Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`") Long correlativo,
            @NotNull @Schema(example = "2026-10-20", description = "Fecha de emisión (`YYYY-MM-DD`), no futura; SUNAT la recibe hasta 7 días calendario desde el día siguiente (2600)") LocalDate fechaEmision,
            @NotNull @Valid @Schema(description = "El proveedor al que se retiene: siempre con RUC") ProveedorDto proveedor,
            @NotBlank @Pattern(regexp = "\\d{2}") @Schema(example = "01", description = "Régimen de retención, catálogo 23: `01` tasa 3 % (el vigente) o `02` tasa 6 % (solo para comprobantes emitidos hasta el 28/02/2014, 2985)") String regimen,
            @DecimalMin("0.01") @Digits(integer = 1, fraction = 2) @Schema(example = "3", description = "Tasa en porcentaje; si se omite, la del régimen. Otra que no sea la del régimen responde `422` (2619)") BigDecimal tasa,
            @NotEmpty @Valid @ArraySchema(minItems = 1, maxItems = ComprobanteDeAgente.MAX_DOCUMENTOS, arraySchema = @Schema(description = "Los comprobantes del proveedor que se pagan, cada uno con su pago")) List<@NotNull @Valid DocumentoDto> documentos,
            @Schema(example = "true", description = "`true` (por defecto) envía a SUNAT en la misma llamada; `false` lo deja `FIRMADO` para enviarlo con `POST /v1/facturas/{id}/enviar`") Boolean enviarAutomatico,
            @Size(max = 1000) @Schema(example = "Pago de facturas de septiembre.", description = "Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML") String observaciones) {

        @Schema(name = "ProveedorDeRetencion")
        public record ProveedorDto(
                @NotBlank @Pattern(regexp = "\\d{11}", message = "RUC de 11 dígitos") @Schema(example = "20131312955", description = "RUC del proveedor (2724), distinto del de la empresa (2620)") String ruc,
                @NotBlank @Size(max = 1500) @Schema(example = "PROVEEDOR DEL NORTE S.A.C.", description = "Razón social (2133, 2134)") String razonSocial,
                @Size(max = 100) @Schema(example = "Av. Grau 123, Piura", description = "Dirección, en una línea de hasta 100 caracteres. Opcional") String direccion) {}

        public EmitirAgenteCommand aComando() {
            return comando(serie, correlativo, fechaEmision, new Receptor("6", proveedor.ruc(), proveedor.razonSocial(), proveedor.direccion()), TipoDocumento.RETENCION, regimen, tasa,
                    documentos, enviarAutomatico, observaciones);
        }
    }

    /** Cuerpo de {@code POST /v1/percepciones}. */
    @Schema(name = "PercepcionRequest")
    public record Percepcion(
            @NotBlank @Pattern(regexp = "P[A-Z0-9]{3}", message = "serie de percepción inválida (P + 3 alfanuméricos, p. ej. P001)") @Schema(example = "P001", description = "Serie registrada con el tipo `40`: `P` + 3 alfanuméricos (regla 1001)") String serie,
            @Positive @Schema(example = "15", description = "Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`") Long correlativo,
            @NotNull @Schema(example = "2026-10-20", description = "Fecha de emisión (`YYYY-MM-DD`), no futura; SUNAT la recibe hasta 7 días calendario desde el día siguiente (2600)") LocalDate fechaEmision,
            @NotNull @Valid @Schema(description = "El cliente al que se percibe") ClienteDto cliente,
            @NotBlank @Pattern(regexp = "\\d{2}") @Schema(example = "01", description = "Régimen de percepción, catálogo 22: `01` venta interna 2 %, `02` adquisición de combustible 1 %, `03` agente de percepción con tasa especial 0.5 %") String regimen,
            @DecimalMin("0.01") @Digits(integer = 1, fraction = 2) @Schema(example = "2", description = "Tasa en porcentaje; si se omite, la del régimen. Otra que no sea la del régimen responde `422` (2603)") BigDecimal tasa,
            @NotEmpty @Valid @ArraySchema(minItems = 1, maxItems = ComprobanteDeAgente.MAX_DOCUMENTOS, arraySchema = @Schema(description = "Los comprobantes de la empresa que se cobran, cada uno con su cobro")) List<@NotNull @Valid DocumentoDto> documentos,
            @Schema(example = "true", description = "`true` (por defecto) envía a SUNAT en la misma llamada; `false` lo deja `FIRMADO` para enviarlo con `POST /v1/facturas/{id}/enviar`") Boolean enviarAutomatico,
            @Size(max = 1000) @Schema(example = "Cobro de la factura F001-9.", description = "Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML") String observaciones) {

        @Schema(name = "ClienteDePercepcion")
        public record ClienteDto(
                @NotBlank @Schema(example = "6", description = "Tipo de documento, catálogo 06 (`6` RUC, `1` DNI, `4` carné de extranjería, `7` pasaporte…; 2511)") String tipoDoc,
                @NotBlank @Size(max = 15) @Schema(example = "20601234565", description = "Número de documento, alfanumérico de hasta 15 caracteres (2680), distinto del RUC de la empresa (2604)") String numDoc,
                @NotBlank @Size(max = 1500) @Schema(example = "CLIENTE MAYORISTA S.A.C.", description = "Razón social o nombre (2133, 2134)") String nombre,
                @Size(max = 100) @Schema(example = "Jr. Huallaga 450, Lima", description = "Dirección, en una línea de hasta 100 caracteres. Opcional") String direccion) {}

        public EmitirAgenteCommand aComando() {
            return comando(serie, correlativo, fechaEmision, new Receptor(cliente.tipoDoc(), cliente.numDoc(), cliente.nombre(), cliente.direccion()), TipoDocumento.PERCEPCION, regimen, tasa,
                    documentos, enviarAutomatico, observaciones);
        }
    }
}
