package pe.factura.application.port.out;

import pe.factura.domain.sire.PeriodoSire;
import pe.factura.domain.tenant.Tenant;

import java.util.List;

/**
 * La propuesta del Registro de Ventas e Ingresos Electrónico (RVIE) del SIRE por la API REST de SUNAT (#322), según el «Manual del servicio API del Registro de
 * Ventas e Información Electrónica» v31 (cpe.sunat.gob.pe), servicios 5.18 (descargar propuesta), 5.16 (consultar estado de envío de ticket) y 5.17 (descargar
 * archivo). Solo lee: pide a SUNAT que prepare el archivo de la propuesta ({@link #solicitarPropuesta}), pregunta por el ticket hasta que termine
 * ({@link #consultar}) y baja cada archivo ({@link #descargar}). No reemplaza ni acepta nada en el SIRE.
 *
 * <p>Lo pide con un token OAuth2 del grant {@code password}: las credenciales API SUNAT de la empresa (client_id y client_secret de una aplicación registrada en
 * SOL como «MIGE RCE y RVIE - SIRE») más su usuario y clave SOL.
 *
 * <p>Errores: faltan las credenciales API SUNAT o SOL → {@code DomainException} (CREDENCIALES_API_SUNAT_NO_CARGADAS, CREDENCIALES_SOL_NO_CARGADAS); el servidor
 * no tiene encendida la conexión con el SIRE → {@code DomainException("SIRE_NO_DISPONIBLE")}; SUNAT no acepta las credenciales →
 * {@link SunatCredencialesException}; rechaza el pedido por sus datos (HTTP 400/422, p. ej. 1070 «no hay información de comprobantes en el periodo») →
 * {@link SunatRechazoException}; no responde o falla → {@link SunatTransientException}.
 */
public interface SunatSireGateway {
    /** Cómo va el ticket: {@code TERMINADO} ya tiene los archivos para bajar; {@code CON_ERRORES} no los tendrá. */
    enum Fase { EN_PROCESO, TERMINADO, CON_ERRORES }

    /**
     * Un archivo que SUNAT generó para el ticket, con lo que el servicio de descarga pide de la consulta del ticket (manual, 5.17): {@code nombre}
     * ({@code nomArchivoReporte}), {@code tipo} ({@code codTipoArchivoReporte}, nulo si SUNAT lo manda nulo), {@code proceso} ({@code codProceso}) y
     * {@code periodo} ({@code perTributario}).
     */
    record ArchivoDelTicket(String ticket, String nombre, String tipo, String proceso, String periodo) {}

    /**
     * La consulta del ticket: {@code codigoSunat} y {@code descripcionSunat} son el estado de envío tal como lo da SUNAT (Anexo III del manual), para mostrarlo;
     * {@code archivos} está lleno solo con {@link Fase#TERMINADO}. Un periodo grande llega partido en varios archivos.
     */
    record EstadoDelTicket(String ticket, Fase fase, String codigoSunat, String descripcionSunat, List<ArchivoDelTicket> archivos) {
        public EstadoDelTicket { archivos = archivos == null ? List.of() : List.copyOf(archivos); }
    }

    /** Si el servidor tiene encendida la conexión con el SIRE (SUNAT no publica una instancia de pruebas y el contrato aún no se verificó contra la real). */
    boolean disponible();

    /** Pide el archivo de la propuesta del periodo (texto) y devuelve el número de ticket con que se sigue. */
    String solicitarPropuesta(Tenant t, PeriodoSire periodo);

    EstadoDelTicket consultar(Tenant t, PeriodoSire periodo, String ticket);

    /** El archivo tal como lo da SUNAT (un ZIP con el {@code .txt}). Si pasa de {@code maximoBytes}, {@code DomainException("RVIE_PROPUESTA_DEMASIADO_GRANDE")}. */
    byte[] descargar(Tenant t, ArchivoDelTicket archivo, long maximoBytes);
}
