package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * El padrón reducido de SUNAT (#361): un contribuyente por RUC, de toda la plataforma (no es de una empresa). Se reemplaza entero, de una vez, con el archivo
 * que SUNAT publica; en medio de una carga, las consultas siguen viendo el padrón anterior.
 */
public interface PadronRucRepository {
    /** Un renglón del padrón. {@code estado} y {@code condicion} son los textos de SUNAT; {@code ubigeo} y {@code direccion} faltan en casi todos los RUC 10. */
    record Contribuyente(String ruc, String razonSocial, String estado, String condicion, String ubigeo, String direccion) {
        public static final String ACTIVO = "ACTIVO";
        public static final String HABIDO = "HABIDO";

        public boolean activo() { return ACTIVO.equals(estado); }
        public boolean habido() { return HABIDO.equals(condicion); }
    }

    /** Lo que cambió una carga. {@code filas}: las que traía el archivo y se aceptaron. */
    record ResultadoCarga(long filas, long nuevas, long cambiadas, long borradas) { }

    /** Una carga ya terminada, exitosa o no, tal como queda en el registro. */
    record CargaRegistrada(Instant iniciada, Instant terminada, boolean exitosa, String origen, long filas, long descartadas, long nuevas, long cambiadas, long borradas, String error) { }

    Optional<Contribuyente> buscar(String ruc);

    /** La última carga que terminó bien; vacía si el padrón nunca se cargó. */
    Optional<CargaRegistrada> ultimaCargaExitosa();

    /**
     * Deja el padrón igual al archivo: agrega los RUC nuevos, actualiza los que cambiaron y borra los que ya no vienen, todo en una sola transacción. Si algo falla,
     * o el archivo trae menos de {@code minimoDeFilas} (un archivo cortado no debe borrar el padrón), queda el padrón anterior. Consume {@code filas} una sola vez.
     * {@code CARGA_EN_CURSO} si otra carga está corriendo; {@code PADRON_INCOMPLETO} si traía menos del mínimo.
     */
    ResultadoCarga reemplazar(Stream<Contribuyente> filas, long minimoDeFilas);

    void registrar(CargaRegistrada carga);
}
