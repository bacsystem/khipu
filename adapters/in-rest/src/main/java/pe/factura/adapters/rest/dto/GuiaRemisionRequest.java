package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.factura.application.port.in.EmitirGuiaRemisionCommand;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Cuerpo de {@code POST /v1/guias-remision} (#39). La letra de la serie decide la guía, como en las facturas: {@code T###} remitente (09), {@code V###}
 * transportista (31). Cada una lleva sus datos y no los de la otra: la remitente, {@code motivo}, {@code modalidad} y, en transporte público,
 * {@code transportista}; la transportista, {@code remitente} y {@code registro_mtc}. Las reglas de SUNAT las valida el dominio y vuelven como 422 con su código.
 */
@Schema(name = "GuiaRemisionRequest")
public record GuiaRemisionRequest(
        @NotBlank @Pattern(regexp = "[TV][A-Z0-9]{3}", message = "serie de guía remitente (T###) o transportista (V###) inválida")
        @Schema(example = "T001", description = "Serie registrada en `POST /v1/series` (tipo `09` o `31`). Su letra decide la guía: `T` + 3 alfanuméricos la remitente (09), `V` + 3 alfanuméricos la transportista (31)") String serie,
        @Schema(example = "2026-10-20", description = "Fecha de emisión (`YYYY-MM-DD`), no futura. Por defecto, hoy") LocalDate fechaEmision,
        @NotNull @Valid @Schema(description = "Quién recibe los bienes (cualquier documento del catálogo 06)") Parte destinatario,
        @Pattern(regexp = "\\d{2}") @Schema(example = "01", description = "Solo remitente. Motivo de traslado, catálogo 20 (`GET /v1/catalogos/20`): `01` venta, `02` compra, `03` venta con entrega a terceros, `04` traslado entre establecimientos de la misma empresa, `05` consignación, `06` devolución, `07` recojo de bienes transformados, `13` otros, `14` venta sujeta a confirmación, `17` traslado para transformación. Aún no: `08` importación, `09` exportación y `18` emisor itinerante") String motivo,
        @Size(max = 100) @Schema(example = "Venta", description = "Solo remitente. Descripción del motivo (hasta 100 caracteres); obligatoria con `13` (otros). Por defecto, la del catálogo") String descripcionMotivo,
        @Pattern(regexp = "0[12]") @Schema(example = "02", description = "Solo remitente. Modalidad, catálogo 18: `01` transporte público (va `transportista`, sin vehículos ni conductores) o `02` privado (van `vehiculos` y `conductores`)") String modalidad,
        @Valid @Schema(description = "Solo remitente en transporte público: la empresa de transporte") Transportista transportista,
        @Valid @Schema(description = "Solo transportista: quien envía los bienes") Parte remitente,
        @Size(max = 20) @Schema(example = "1512345CNG", description = "Solo transportista: su número de registro del MTC (obligatorio, regla 4391)") String registroMtc,
        @NotNull @Valid @Schema(description = "Datos del traslado") Envio traslado,
        @NotEmpty @Size(max = 9999) @ArraySchema(arraySchema = @Schema(description = "Bienes que se trasladan, de 1 a 9999")) List<@Valid Item> items,
        @Size(max = 50) @ArraySchema(arraySchema = @Schema(description = "Documentos que sustentan el traslado (catálogo 61): la factura de la venta, la guía remitente que lleva el transportista, la DAM…")) List<@Valid Documento> documentosRelacionados,
        @Size(max = 250) @Schema(example = "Mercadería frágil", description = "Observaciones (`cbc:Note`), hasta 250 caracteres en una línea") String observaciones,
        @Schema(example = "true", description = "Enviar a SUNAT en la misma llamada (por defecto `true`). Con `false` queda `FIRMADO` y se envía con `POST /v1/guias-remision/{id}/enviar`") Boolean enviarAutomatico) {

    @Schema(name = "GuiaRemisionParteRequest")
    public record Parte(
            @NotBlank @Schema(example = "6", description = "Tipo de documento, catálogo 06") String tipoDoc,
            @NotBlank @Size(max = 15) @Schema(example = "20601234565") String numDoc,
            @NotBlank @Size(max = 250) @Schema(example = "COMERCIAL ANDINA S.A.C.", description = "Nombre o razón social, de 3 a 250 caracteres") String nombre) {
        ParteDeGuia aDominio() { return new ParteDeGuia(tipoDoc, numDoc, nombre); }
    }

    @Schema(name = "GuiaRemisionTransportistaRequest")
    public record Transportista(
            @NotBlank @Schema(example = "20480072872") String ruc,
            @NotBlank @Size(max = 250) @Schema(example = "TRANSPORTES DEL SUR S.A.C.") String razonSocial,
            @Size(max = 20) @Schema(example = "1512345CNG", description = "Número de registro del MTC (opcional)") String registroMtc) {}

    @Schema(name = "GuiaRemisionEnvioRequest")
    public record Envio(
            @Schema(example = "2026-10-21", description = "Inicio del traslado (o entrega al transportista), no anterior a la emisión (3343). Obligatoria") LocalDate fechaInicio,
            @Schema(example = "25.5", description = "Peso bruto total, mayor que cero, hasta 3 decimales (2880). Obligatorio") BigDecimal pesoBruto,
            @Pattern(regexp = "KGM|TNE") @Schema(example = "KGM", description = "`KGM` (por defecto) o `TNE`") String unidadPeso,
            @Schema(example = "null", description = "Bultos o pallets: solo en una importación (4195), que khipu aún no emite; debe omitirse") Integer bultos,
            @NotNull @Valid Punto partida,
            @NotNull @Valid Punto llegada,
            @Size(max = 3) @ArraySchema(arraySchema = @Schema(description = "El primero es el principal; hasta dos secundarios (4389)")) List<@Valid Vehiculo> vehiculos,
            @Size(max = 3) @ArraySchema(arraySchema = @Schema(description = "El primero es el principal; hasta dos secundarios (4376)")) List<@Valid Conductor> conductores,
            @ArraySchema(arraySchema = @Schema(description = "Indicadores especiales: `vehiculo_m1_o_l` (autos o motos: sin vehículo ni conductor), `retorno_vehiculo_envase_vacio`, `retorno_vehiculo_vacio`, `transbordo_programado`, `vehiculos_conductores_transportista` (público: el remitente registra los del transportista)"))
            List<String> indicadores) {}

    @Schema(name = "GuiaRemisionPuntoRequest")
    public record Punto(
            @Schema(example = "150101", description = "Ubigeo del catálogo 13. Con solo `establecimiento` (de la propia empresa en una guía remitente) khipu lo completa") String ubigeo,
            @Size(max = 500) @Schema(example = "Av. Argentina 1234, Cercado de Lima", description = "Dirección detallada, de 3 a 500 caracteres") String direccion,
            @Schema(example = "0001", description = "Código de 4 dígitos del establecimiento anexo (`0000` el domicilio fiscal). Los de la empresa se toman de `GET /v1/empresa/establecimientos`") String establecimiento,
            @Schema(example = "20100066603", description = "RUC al que pertenece el establecimiento (3410); por defecto el del remitente") String ruc) {
        PuntoDeTraslado aDominio() { return new PuntoDeTraslado(ubigeo, direccion, establecimiento, ruc); }
    }

    @Schema(name = "GuiaRemisionVehiculoRequest")
    public record Vehiculo(@NotBlank @Schema(example = "ABC123", description = "Placa, de 6 a 8 letras o dígitos (los guiones se quitan)") String placa) {}

    @Schema(name = "GuiaRemisionConductorRequest")
    public record Conductor(
            @NotBlank @Schema(example = "1", description = "Tipo de documento, catálogo 06 (1 DNI, 4 carné de extranjería, 7 pasaporte…)") String tipoDoc,
            @NotBlank @Schema(example = "45678912") String numDoc,
            @NotBlank @Size(max = 250) @Schema(example = "JUAN") String nombres,
            @NotBlank @Size(max = 250) @Schema(example = "QUISPE MAMANI") String apellidos,
            @NotBlank @Schema(example = "Q45678912", description = "Licencia de conducir, de 9 a 10 letras o dígitos") String licencia) {}

    @Schema(name = "GuiaRemisionItemRequest")
    public record Item(
            @Size(max = 30) @Schema(example = "LAP-15") String codigo,
            @NotBlank @Size(max = 500) @Schema(example = "Laptop 15 pulgadas") String descripcion,
            @NotBlank @Schema(example = "NIU", description = "Unidad, catálogo 03") String unidad,
            @NotNull @Schema(example = "2") BigDecimal cantidad,
            @Schema(example = "43211503", description = "Código de producto SUNAT (UNSPSC, 8 dígitos), opcional") String codigoSunat) {}

    @Schema(name = "GuiaRemisionDocumentoRequest")
    public record Documento(
            @NotBlank @Schema(example = "01", description = "Tipo, catálogo 61 (01 factura, 03 boleta, 09 guía remitente, 50 DAM…)") String tipo,
            @NotBlank @Size(max = 100) @Schema(example = "F001-123", description = "Número; serie-número en los comprobantes y guías") String numero,
            @Schema(example = "20100066603", description = "RUC de quien emitió el documento; obligatorio en los comprobantes y guías") String rucEmisor) {}

    public EmitirGuiaRemisionCommand aComando() {
        boolean transportista31 = serie != null && serie.startsWith("V");
        DatosDeGuia datos;
        if (transportista31) {
            if (motivo != null || descripcionMotivo != null || modalidad != null || transportista != null)
                throw new DomainException("GUIA_INVALIDA", "Una guía transportista (serie V###) no lleva motivo, descripcion_motivo, modalidad ni transportista: esos son datos de la guía remitente");
            datos = new DatosDeGuia.DeTransportista(remitente == null ? null : remitente.aDominio(), registroMtc);
        } else {
            if (remitente != null || registroMtc != null)
                throw new DomainException("GUIA_INVALIDA", "Una guía remitente (serie T###) no lleva remitente ni registro_mtc: esos son datos de la guía transportista");
            datos = new DatosDeGuia.DeRemitente(motivo, descripcionMotivo, modalidad == null ? null : ModalidadTraslado.porCodigo(modalidad),
                    transportista == null ? null : new TransportistaContratado(transportista.ruc(), transportista.razonSocial(), transportista.registroMtc()));
        }
        Envio e = traslado;
        Set<IndicadorDeTraslado> ind = EnumSet.noneOf(IndicadorDeTraslado.class);
        if (e.indicadores() != null) e.indicadores().forEach(i -> ind.add(IndicadorDeTraslado.porCodigo(i)));
        Traslado t = new Traslado(e.fechaInicio(), e.pesoBruto(), e.unidadPeso(), e.bultos(), e.partida().aDominio(), e.llegada().aDominio(),
                e.vehiculos() == null ? List.of() : e.vehiculos().stream().map(v -> new pe.factura.domain.documento.Vehiculo(v.placa())).toList(),
                e.conductores() == null ? List.of() : e.conductores().stream().map(c -> new pe.factura.domain.documento.Conductor(c.tipoDoc(), c.numDoc(), c.nombres(), c.apellidos(), c.licencia())).toList(),
                ind);
        List<ItemDeGuia> lineas = items.stream().map(i -> new ItemDeGuia(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.codigoSunat())).toList();
        List<DocumentoDeTraslado> docs = documentosRelacionados == null ? List.of()
                : documentosRelacionados.stream().map(d -> new DocumentoDeTraslado(d.tipo(), d.numero(), d.rucEmisor())).toList();
        return new EmitirGuiaRemisionCommand(serie, fechaEmision, destinatario.aDominio(), datos, t, lineas, docs, observaciones, enviarAutomatico == null || enviarAutomatico);
    }
}
