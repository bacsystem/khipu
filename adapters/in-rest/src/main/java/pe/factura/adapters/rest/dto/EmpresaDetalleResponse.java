package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.DomicilioDeEmpresa;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.EmpresaDetalle;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.domain.tenant.Entorno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una empresa para el backoffice (#186): lo mismo que ve su dueño, en solo lectura y sin secretos. No lleva el hash de las API
 * keys, ni dónde está guardado el logo, ni nada del certificado ni de las credenciales SOL.
 */
public record EmpresaDetalleResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
        @Schema(example = "20100066603") String ruc,
        @Schema(example = "COMERCIAL ANDINA SAC") String razonSocial,
        @Schema(example = "ANDINA", description = "Ausente si no tiene nombre comercial") String nombreComercial,
        Entorno entorno,
        @Schema(example = "2026-09-01T10:00:00Z", description = "Fecha de alta") Instant creadaEn,
        @Schema(description = "Cuenta dueña; ausente en las empresas dadas de alta por una integración, que no tienen cuenta") UUID cuentaId,
        @Schema(example = "Mi negocio") String cuentaNombre,
        @Schema(description = "Estado del certificado hoy (fecha de Lima), con la misma regla del listado de empresas") EstadoCertificado certificado,
        @Schema(example = "2027-03-01", description = "Hasta cuándo vale el certificado; ausente si no hay certificado o no se conoce") LocalDate certificadoVigenteHasta,
        @Schema(example = "17", description = "Días hasta esa fecha; negativo si ya venció; ausente si no hay fecha") Integer certificadoDiasRestantes,
        @Schema(description = "Si están cargados el usuario y la clave SOL; nunca se exponen") boolean tieneCredencialesSol,
        @Schema(description = "Domicilio fiscal; ausente si la empresa todavía no lo declaró") DomicilioResponse domicilio,
        @Schema(example = "00-123-456789") String cuentaDetracciones,
        @Schema(description = "Si figura en el padrón de contribuyentes con tasa especial de IGV") boolean padronTasaEspecialIgv,
        PdfResponse pdf,
        List<SerieResponse> series,
        List<EstablecimientoResponse> establecimientos,
        @Schema(description = "Sin el secreto: solo el prefijo con el que el dueño reconoce cada key") List<ApiKeyResponse> apiKeys,
        @Schema(description = "Los 10 comprobantes de fecha de emisión más reciente, con el detalle del CDR de SUNAT") List<ComprobanteResponse> comprobantes,
        @Schema(description = "Los últimos 20 cambios de estado de esos comprobantes, del más reciente al más antiguo") List<EventoResponse> eventos,
        OutboxResponse outbox) {

    public record DomicilioResponse(
            @Schema(example = "150122") String ubigeo,
            @Schema(example = "AV. LARCO 345") String direccion,
            String urbanizacion,
            @Schema(example = "MIRAFLORES") String distrito,
            @Schema(example = "LIMA") String provincia,
            @Schema(example = "LIMA") String departamento,
            @Schema(example = "0000", description = "Código de establecimiento; 0000 es el domicilio fiscal") String codigoEstablecimiento) {
        static DomicilioResponse de(DomicilioDeEmpresa d) {
            return d == null ? null : new DomicilioResponse(d.ubigeo(), d.direccion(), d.urbanizacion(), d.distrito(), d.provincia(), d.departamento(), d.codigoEstablecimiento());
        }
    }

    public record PdfResponse(
            @Schema(example = "CLASICO", description = "CLASICO, MODERNO, SUTIL, CORPORATIVO o GRIS") String plantilla,
            @Schema(example = "#1E1E24") String colorPrimario,
            @Schema(description = "Si hay un logo cargado; no se expone dónde está guardado") boolean tieneLogo,
            String pieDePagina,
            String observacionesPorDefecto) {}

    public record SerieResponse(
            @Schema(example = "01", description = "Tipo de comprobante (catálogo 01)") String tipo,
            @Schema(example = "F001") String codigo,
            @Schema(example = "12", description = "Último número emitido") long ultimoNumero,
            boolean activa,
            @Schema(example = "0000") String establecimiento) {}

    public record EstablecimientoResponse(
            @Schema(example = "0002") String codigo,
            @Schema(example = "Tienda Surco") String nombre,
            DomicilioResponse domicilio,
            boolean activo) {}

    public record ApiKeyResponse(
            UUID id,
            @Schema(example = "fk_demo001") String prefijo,
            boolean activa,
            Instant creadaEn,
            @Schema(description = "Ausente si sigue vigente") Instant revocadaEn) {}

    public record CdrResponse(
            @Schema(example = "0", description = "0 = aceptada") String codigo,
            @Schema(example = "La Factura numero F001-12, ha sido aceptada") String descripcion,
            @Schema(description = "Observaciones de SUNAT; vacía si no hay ninguna") List<String> observaciones) {}

    public record ComprobanteResponse(
            UUID id,
            @Schema(example = "01", description = "Tipo de comprobante (catálogo 01)") String tipo,
            @Schema(example = "F001") String serie,
            @Schema(example = "12") long numero,
            @Schema(example = "2026-09-30") LocalDate fechaEmision,
            @Schema(example = "ACEPTADO") String estado,
            @Schema(example = "PEN") String moneda,
            @Schema(example = "118.00") BigDecimal total,
            @Schema(description = "Intentos de envío a SUNAT") int intentos,
            @Schema(description = "Error del último intento; ausente si no falló") String ultimoError,
            @Schema(description = "CDR de SUNAT; ausente si todavía no respondió") CdrResponse cdr) {}

    public record EventoResponse(
            @Schema(example = "F001-00000012", description = "Comprobante al que pertenece el cambio") String comprobante,
            @Schema(example = "FIRMADO", description = "Ausente en el primer evento de un comprobante") String estadoAnterior,
            @Schema(example = "ACEPTADO") String estadoNuevo,
            String detalle,
            @Schema(example = "2026-09-30T15:00:00Z") Instant ocurridoEn) {}

    public record OutboxResponse(
            @Schema(example = "12", description = "Tareas pendientes de esta empresa; el outbox se vacía al completar cada una") long total,
            @Schema(description = "Las próximas 10 en intentarse") List<TareaResponse> proximas) {}

    public record TareaResponse(
            @Schema(example = "DOCUMENTO") String agregado,
            UUID agregadoId,
            @Schema(example = "ENVIAR") String accion,
            int intentos,
            Instant siguienteIntento,
            @Schema(description = "Error del último intento; ausente si todavía no falló") String ultimoError) {}

    public static EmpresaDetalleResponse de(EmpresaDetalle d) {
        return new EmpresaDetalleResponse(d.id(), d.ruc(), d.razonSocial(), d.nombreComercial(), d.entorno(), d.creadaEn(), d.cuentaId(), d.cuentaNombre(),
                d.certificado(), d.certificadoVigenteHasta(), d.certificadoDiasRestantes(), d.tieneCredencialesSol(), DomicilioResponse.de(d.domicilio()),
                d.cuentaDetracciones(), d.padronTasaEspecialIgv(),
                new PdfResponse(d.pdf().plantilla(), d.pdf().colorPrimario(), d.pdf().tieneLogo(), d.pdf().pieDePagina(), d.pdf().observacionesPorDefecto()),
                d.series().stream().map(s -> new SerieResponse(s.tipo(), s.codigo(), s.ultimoNumero(), s.activa(), s.establecimiento())).toList(),
                d.establecimientos().stream().map(e -> new EstablecimientoResponse(e.domicilio().codigoEstablecimiento(), e.nombre(), DomicilioResponse.de(e.domicilio()), e.activo())).toList(),
                d.apiKeys().stream().map(k -> new ApiKeyResponse(k.id(), k.prefijo(), k.activa(), k.creadaEn(), k.revocadaEn())).toList(),
                d.comprobantes().stream().map(c -> new ComprobanteResponse(c.id(), c.tipo(), c.serie(), c.numero(), c.fechaEmision(), c.estado(), c.moneda(), c.total(),
                        c.intentos(), c.ultimoError(), c.cdr() == null ? null : new CdrResponse(c.cdr().codigo(), c.cdr().descripcion(), c.cdr().observaciones()))).toList(),
                d.eventos().stream().map(e -> new EventoResponse(String.format("%s-%08d", e.serie(), e.numero()), e.estadoAnterior(), e.estadoNuevo(), e.detalle(), e.ocurridoEn())).toList(),
                new OutboxResponse(d.outbox().total(), d.outbox().proximas().stream().map(t -> new TareaResponse(t.agregado(), t.agregadoId(), t.accion(), t.intentos(),
                        t.siguienteIntento(), t.ultimoError())).toList()));
    }
}
