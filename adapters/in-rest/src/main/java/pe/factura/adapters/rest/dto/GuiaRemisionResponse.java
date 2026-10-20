package pe.factura.adapters.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Una guía de remisión (#39) tal como la ve la API. Los bloques que no son del tipo de la guía no se serializan. */
@Schema(name = "GuiaRemisionResponse")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuiaRemisionResponse(
        @Schema(description = "Id de la guía; en `/validar` es provisional y no existe") UUID id,
        @Schema(example = "09", description = "`09` remitente o `31` transportista") String tipo,
        @Schema(example = "T001") String serie,
        @Schema(example = "15", description = "Correlativo; `null` en `/validar`") Long numero,
        @Schema(example = "20100066603-09-T001-15") String nombreArchivo,
        @Schema(example = "2026-10-20") LocalDate fechaEmision,
        @Schema(example = "10:04:05") String horaEmision,
        @Schema(example = "ACEPTADO", description = "`FIRMADO`, `ERROR_ENVIO`, `ENVIADO_PENDIENTE_TICKET`, `ACEPTADO`, `ACEPTADO_CON_OBS` o `RECHAZADO` (`RECIBIDO` solo en `/validar`)") String estado,
        Parte destinatario,
        @Schema(example = "01") String motivo,
        @Schema(example = "Venta") String descripcionMotivo,
        @Schema(example = "02") String modalidad,
        Transportista transportista,
        Parte remitente,
        @Schema(example = "1512345CNG") String registroMtc,
        Envio traslado,
        List<Item> items,
        List<Documento> documentosRelacionados,
        String observaciones,
        @Schema(description = "Valor resumen de la firma (DigestValue)") String hash,
        @Schema(example = "7f7b00ab-4fee-4ae2-9e04-fbd16ca18677", description = "Ticket que dio SUNAT al recibirla") String ticket,
        @Schema(example = "2026-10-20T10:04:07", description = "Fecha de recepción que informó SUNAT (hora de Lima)") LocalDateTime fechaRecepcion,
        @Schema(example = "1", description = "Envíos intentados") int intentos,
        @Schema(example = "2", description = "Consultas del ticket hechas") int consultasTicket,
        @Schema(description = "Si khipu sigue solo: reintenta el envío (ERROR_ENVIO) o consulta el ticket (ENVIADO_PENDIENTE_TICKET). `false` en ERROR_ENVIO: corrija lo que dice `ultimo_error` y reenvíe; en ENVIADO_PENDIENTE_TICKET: reconsulte el ticket") boolean reintentoAutomatico,
        @Schema(description = "El último fallo del envío o de la consulta del ticket") String ultimoError,
        Cdr cdr,
        @Schema(description = "URL de consulta de la guía que trae el CDR (la del QR de la representación impresa), si la trae") String enlaceConsulta,
        @Schema(description = "Descargas; ausente en `/validar`") Enlaces enlaces) {

    @Schema(name = "GuiaRemisionParteResponse")
    public record Parte(String tipoDoc, String numDoc, String nombre) {
        static Parte de(ParteDeGuia p) { return p == null ? null : new Parte(p.tipoDoc(), p.numDoc(), p.nombre()); }
    }

    @Schema(name = "GuiaRemisionTransportistaResponse")
    public record Transportista(String ruc, String razonSocial, String registroMtc) {}

    @Schema(name = "GuiaRemisionEnvioResponse")
    public record Envio(LocalDate fechaInicio, BigDecimal pesoBruto, String unidadPeso, Punto partida, Punto llegada, List<Vehiculo> vehiculos, List<Conductor> conductores, List<String> indicadores) {}

    @Schema(name = "GuiaRemisionPuntoResponse")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Punto(String ubigeo, String direccion, String establecimiento, String ruc) {
        static Punto de(PuntoDeTraslado p) { return new Punto(p.ubigeo(), p.direccion(), p.codigoEstablecimiento(), p.rucEstablecimiento()); }
    }

    @Schema(name = "GuiaRemisionVehiculoResponse")
    public record Vehiculo(String placa, @Schema(description = "El primero es el principal") boolean principal) {}

    @Schema(name = "GuiaRemisionConductorResponse")
    public record Conductor(String tipoDoc, String numDoc, String nombres, String apellidos, String licencia, boolean principal) {}

    @Schema(name = "GuiaRemisionItemResponse")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, String codigoSunat) {}

    @Schema(name = "GuiaRemisionDocumentoResponse")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Documento(String tipo, String descripcion, String numero, String rucEmisor) {}

    @Schema(name = "GuiaRemisionCdrResponse")
    public record Cdr(String codigo, String descripcion, List<String> observaciones) {}

    @Schema(name = "GuiaRemisionEnlacesResponse")
    public record Enlaces(String xml, String pdf, String cdr) {}

    /** {@code base}: la ruta de las guías; {@code null} en {@code /validar}, que no tiene descargas. */
    public static GuiaRemisionResponse de(GuiaRemision g, String base) {
        Traslado t = g.traslado();
        List<Vehiculo> vehiculos = new java.util.ArrayList<>();
        for (int i = 0; i < t.vehiculos().size(); i++) vehiculos.add(new Vehiculo(t.vehiculos().get(i).placa(), i == 0));
        List<Conductor> conductores = new java.util.ArrayList<>();
        for (int i = 0; i < t.conductores().size(); i++) {
            pe.factura.domain.documento.Conductor c = t.conductores().get(i);
            conductores.add(new Conductor(c.tipoDoc(), c.numDoc(), c.nombres(), c.apellidos(), c.licencia(), i == 0));
        }
        Envio envio = new Envio(t.fechaInicio(), t.pesoBruto(), t.unidadPeso(), Punto.de(t.partida()), Punto.de(t.llegada()), vehiculos, conductores,
                t.indicadores().stream().sorted().map(IndicadorDeTraslado::codigo).toList());
        DatosDeGuia.DeRemitente r = g.remitente().orElse(null);
        DatosDeGuia.DeTransportista tr = g.transportista().orElse(null);
        TransportistaContratado tc = r == null ? null : r.transportista();
        boolean conEnlaces = base != null && g.numero() != null;
        String raiz = conEnlaces ? base + "/" + g.id() : null;
        return new GuiaRemisionResponse(g.id(), g.tipo().codigo(), g.serie(), g.numero(), g.nombreArchivo(), g.fechaEmision(),
                g.horaEmision() == null ? null : g.horaEmision().format(DateTimeFormatter.ofPattern("HH:mm:ss")), g.estado().name(), Parte.de(g.destinatario()),
                r == null ? null : r.motivo(), r == null ? null : r.descripcionMotivo(), r == null ? null : r.modalidad().codigo(),
                tc == null ? null : new Transportista(tc.ruc(), tc.razonSocial(), tc.registroMtc()),
                tr == null ? null : Parte.de(tr.remitente()), tr == null ? null : tr.registroMtc(), envio,
                g.items().stream().map(i -> new Item(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.codigoSunat())).toList(),
                g.documentos().stream().map(d -> new Documento(d.tipo(), d.descripcionTipo(), d.numero(), d.rucEmisor())).toList(),
                g.observaciones(), g.hash(), g.ticket(), g.fechaRecepcion(), g.intentos(), g.consultasTicket(), g.reintentoAutomatico(), g.ultimoError(),
                g.cdr() == null ? null : new Cdr(g.cdr().codigo(), g.cdr().descripcion(), g.cdr().observaciones()), g.enlaceConsulta(),
                conEnlaces ? new Enlaces(raiz + "/xml", raiz + "/pdf", g.cdrKey() == null ? null : raiz + "/cdr") : null);
    }
}
