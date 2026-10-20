package pe.factura.application.port.in;

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

    record Conciliacion(PeriodoSire periodo, long pendientesDeSunat, ConciliacionRvie.Resultado resultado) {}

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
}
