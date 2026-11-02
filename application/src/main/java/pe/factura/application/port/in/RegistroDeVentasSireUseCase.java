package pe.factura.application.port.in;

import pe.factura.application.port.out.SunatSireGateway;
import pe.factura.domain.sire.ConciliacionRvie;
import pe.factura.domain.sire.PeriodoSire;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * El registro de ventas del SIRE (#322): el archivo de reemplazo de la propuesta del RVIE, en el formato de {@code FormatoRvie}, y la conciliación de lo que khipu
 * emitió con la propuesta que el contribuyente descarga del SIRE.
 */
public interface RegistroDeVentasSireUseCase {

    /** Lo que se va a escribir, ya validado: si algo impide el archivo, {@link #preparar} falla antes de que empiece la respuesta. */
    record Exportacion(UUID tenantId, String ruc, String razonSocial, PeriodoSire periodo, String nombreArchivo, long registros, long pendientesDeSunat, long sinTipoCambio) {}

    /** {@code filasConsolidadas}: filas de la propuesta con el campo 10 (un rango de boletas o tickets del día), que no se concilian una por una. */
    record Conciliacion(PeriodoSire periodo, long pendientesDeSunat, long filasConsolidadas, ConciliacionRvie.Resultado resultado) {}

    /** El ticket con que SUNAT prepara la propuesta del periodo (#322, fase 2). */
    record PropuestaSolicitada(PeriodoSire periodo, String ticket) {}

    /** Cómo va el ticket: con {@code TERMINADO}, {@code archivos} son los que se concilian; {@code codigoSunat} y {@code descripcionSunat} son su estado de envío (Anexo III del manual). */
    record EstadoDePropuesta(PeriodoSire periodo, String ticket, SunatSireGateway.Fase fase, String codigoSunat, String descripcionSunat, int archivos) {}

    /**
     * Valida el periodo ({@code AAAAMM}) y lo que hay en él: sin comprobantes es {@code NO_ENCONTRADO}, más del tope es {@code RVIE_EXCEDE_EL_TOPE}, y comprobantes
     * fuera de soles sin tipo de cambio son {@code RVIE_SIN_TIPO_CAMBIO} salvo {@code permitirSinTipoCambio}.
     */
    Exportacion preparar(UUID tenantId, String periodo, boolean permitirSinTipoCambio);

    /**
     * Escribe el ZIP con el {@code .txt} del registro. Si algo falla a mitad de camino, el ZIP queda sin cerrar (sin directorio central): ningún lector lo abre
     * como si estuviera completo.
     */
    void escribir(Exportacion exportacion, OutputStream salida) throws IOException;

    /** Concilia el periodo con la propuesta de SUNAT ({@code .txt} o {@code .zip} con un {@code .txt}). */
    Conciliacion conciliar(UUID tenantId, String periodo, InputStream propuesta) throws IOException;

    /**
     * Fase 2: le pide a SUNAT, por la API del SIRE con las credenciales API SUNAT y SOL de la empresa, que prepare la propuesta del periodo. Solo lee. Errores:
     * {@code SIRE_NO_DISPONIBLE} (apagado en el servidor), {@code CREDENCIALES_API_SUNAT_NO_CARGADAS}, {@code CREDENCIALES_SOL_NO_CARGADAS},
     * {@code CREDENCIALES_SIRE_RECHAZADAS}, {@code NO_ENCONTRADO} (SUNAT no tiene comprobantes en el periodo), {@code SIRE_RECHAZO} y {@code SUNAT_NO_DISPONIBLE}.
     */
    PropuestaSolicitada solicitarPropuesta(UUID tenantId, String periodo);

    EstadoDePropuesta estadoDePropuesta(UUID tenantId, String periodo, String ticket);

    /**
     * Fase 2: baja la propuesta que SUNAT preparó para el ticket y la concilia como {@link #conciliar}. Si el ticket no terminó, {@code PROPUESTA_NO_LISTA}; si terminó
     * con errores, {@code PROPUESTA_CON_ERRORES}.
     */
    Conciliacion conciliarConSunat(UUID tenantId, String periodo, String ticket) throws IOException;
}
