package pe.factura.application.port.out;

import pe.factura.domain.documento.Comprobante;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los comprobantes de una empresa que van al registro de ventas del SIRE (#322), leídos por tramos para escribir el archivo sin cargar el mes entero en memoria.
 * Qué estados entran y cuáles cuentan como pendientes lo dice el dominio ({@code FilaRvie.INCLUIDOS} y {@code FilaRvie.PENDIENTES}), no la consulta.
 */
public interface RegistroDeVentasRepository {

    /**
     * Lo que se sabe del periodo antes de escribir nada: cuántos comprobantes entran, cuántos siguen en camino a SUNAT, y cuántos de los que entran están fuera de
     * soles sin tipo de cambio, con hasta {@code ejemplos} de ellos ({@code F001-125}).
     */
    record Conteo(long incluidos, long pendientesDeSunat, long sinTipoCambio, List<String> ejemplosSinTipoCambio) {}

    /** Dónde quedó el recorrido: el orden es fecha de emisión, tipo, serie y número. */
    record Posicion(LocalDate fechaEmision, String tipo, String serie, long numero) {
        public static Posicion de(Comprobante c) { return new Posicion(c.fechaEmision(), c.tipo().codigo(), c.serie(), c.numero()); }
    }

    /** Un comprobante por su tipo, serie y número. */
    record Referencia(String tipo, String serie, long numero) {}

    Conteo contar(UUID tenantId, LocalDate desde, LocalDate hasta, int ejemplos);

    /** Hasta {@code limite} comprobantes que entran al registro, emitidos entre las dos fechas (inclusive), después de {@code despuesDe} (nula: desde el primero). */
    List<Comprobante> siguientes(UUID tenantId, LocalDate desde, LocalDate hasta, Posicion despuesDe, int limite);

    /** La fecha de emisión de cada comprobante de la empresa que existe entre {@code referencias}; los que no existen no están en el mapa. */
    Map<Referencia, LocalDate> fechasDeEmision(UUID tenantId, Collection<Referencia> referencias);
}
