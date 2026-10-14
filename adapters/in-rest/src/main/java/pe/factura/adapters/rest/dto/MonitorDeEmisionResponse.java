package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Franja;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Monitor;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Outbox;
import pe.factura.application.port.in.MonitorearEmisionUseCase.ResumenesSinRespuesta;
import pe.factura.application.port.out.SondeoDeSunat.Resultado;

import java.time.Instant;
import java.util.List;

/** El monitor global de emisión del backoffice (#195): lo que ocurre con los comprobantes de todas las empresas, la cola de envíos y si SUNAT contesta. */
public record MonitorDeEmisionResponse(
        @Schema(example = "2026-10-15T15:20:00Z", description = "Cuándo se tomó esta lectura") Instant generadoEn,
        @Schema(description = "Las últimas 24 horas, de la más vieja a la actual; las horas sin comprobantes van en cero") List<FranjaResponse> horas,
        @Schema(description = "El día de Lima (desde su medianoche) hasta ahora") FranjaResponse hoy,
        OutboxResponse outbox,
        @Schema(description = "Resúmenes diarios y comunicaciones de baja que SUNAT recibió (tienen ticket) y no ha respondido pasado el umbral configurado") ResumenesSinRespuestaResponse resumenesSinRespuesta,
        @Schema(description = "Si cada servicio de SUNAT contesta; un servicio sin URL configurada no aparece. Es una lectura guardada hasta 30 segundos") List<SunatResponse> sunat) {

    public record FranjaResponse(
            @Schema(example = "2026-10-15T15:00:00Z", description = "El inicio de la hora (o de la medianoche de Lima en `hoy`)") Instant desde,
            @Schema(example = "120", description = "Comprobantes creados en la franja, de cualquier estado") long total,
            @Schema(example = "100", description = "Aceptados por SUNAT, con o sin observaciones") long aceptados,
            @Schema(example = "3", description = "Rechazados por SUNAT") long rechazados,
            @Schema(example = "2", description = "No llegaron: error de envío o fuera de plazo") long conError,
            @Schema(example = "10", description = "Firmados, enviados o a la espera del resumen diario") long enCamino,
            @Schema(example = "5", description = "Recibidos, inválidos o dados de baja") long otros,
            @Schema(example = "0.0291", description = "Rechazados sobre los que SUNAT ya resolvió (aceptados + rechazados), de 0 a 1; ausente si no resolvió ninguno") Double tasaDeRechazo) {
        static FranjaResponse de(Franja f) {
            return new FranjaResponse(f.desde(), f.total(), f.aceptados(), f.rechazados(), f.conError(), f.enCamino(), f.otros(), f.tasaDeRechazo());
        }
    }

    public record OutboxResponse(
            @Schema(example = "12", description = "Envíos a SUNAT que esperan en la cola") long pendientes,
            @Schema(example = "1", description = "Los que ya tocaba enviar y nadie tomó; no cuenta lo que está en proceso ni lo que espera su reintento") long vencidos,
            @Schema(example = "2026-10-15T12:00:00Z", description = "Desde cuándo está el más antiguo; ausente si la cola está vacía") Instant masViejoDesde,
            @Schema(example = "420", description = "Segundos que lleva vencido el que más espera; ausente si no hay vencidos") Long vencidoHaceSegundos,
            @Schema(description = "Hay envíos vencidos hace más de 5 minutos: el trabajo que vacía la cola probablemente no corre") boolean alerta) {
        static OutboxResponse de(Outbox o) {
            return new OutboxResponse(o.pendientes(), o.vencidos(), o.masViejoDesde(), o.vencidoHace() == null ? null : o.vencidoHace().toSeconds(), o.alerta());
        }
    }

    /**
     * #362 B14. khipu reconsulta el ticket de un resumen o una baja sin abandonarlo nunca, así que uno que SUNAT no resuelve queda ENVIADO para siempre: esta alerta es la
     * única señal de que alguien tiene que mirarlo (el resumen sin respuesta deja la boleta ENVIADA y sin poder recibir notas ni baja).
     */
    public record ResumenesSinRespuestaResponse(
            @Schema(example = "2", description = "Cuántos resúmenes diarios (RC) o comunicaciones de baja (RA) enviados llevan más del umbral sin respuesta") long cantidad,
            @Schema(example = "2026-10-15T05:00:00Z", description = "Cuándo se creó el más antiguo (el ticket se pide al enviarlo, casi al crearlo); ausente si no hay") Instant masViejoDesde,
            @Schema(example = "37200", description = "Segundos que lleva el más antiguo sin respuesta; ausente si no hay") Long masViejoHaceSegundos,
            @Schema(example = "6", description = "Horas sin respuesta a partir de las cuales se alerta (`app.monitor.resumen-sin-respuesta-horas`)") long umbralHoras,
            @Schema(description = "Hay al menos un resumen o baja con ticket sin respuesta pasado el umbral") boolean alerta) {
        static ResumenesSinRespuestaResponse de(ResumenesSinRespuesta r) {
            return new ResumenesSinRespuestaResponse(r.cantidad(), r.masViejoDesde(), r.masViejoHace() == null ? null : r.masViejoHace().toSeconds(), r.umbral().toHours(), r.alerta());
        }
    }

    public record SunatResponse(
            @Schema(example = "ENVIO_PRODUCCION", allowableValues = {"ENVIO_PRODUCCION", "ENVIO_BETA", "CONSULTA_DE_CDR", "CONSULTA_DE_VALIDEZ"}) String servicio,
            @Schema(description = "Contestó con un éxito dentro del plazo") boolean disponible,
            @Schema(example = "140", description = "Milisegundos que tardó en contestar; ausente si no contestó") Long milisegundos,
            @Schema(example = "HTTP 503", description = "Por qué no está disponible (código HTTP, «Sin conexión», «No contestó en N s»); ausente si lo está") String detalle) {
        static SunatResponse de(Resultado r) { return new SunatResponse(r.servicio().name(), r.disponible(), r.milisegundos(), r.detalle()); }
    }

    public static MonitorDeEmisionResponse de(Monitor m) {
        return new MonitorDeEmisionResponse(m.generadoEn(), m.horas().stream().map(FranjaResponse::de).toList(), FranjaResponse.de(m.hoy()), OutboxResponse.de(m.outbox()),
                ResumenesSinRespuestaResponse.de(m.resumenes()), m.sunat().stream().map(SunatResponse::de).toList());
    }
}
