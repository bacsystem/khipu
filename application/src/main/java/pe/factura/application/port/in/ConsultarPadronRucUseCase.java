package pe.factura.application.port.in;

import pe.factura.application.port.out.PadronRucRepository.Contribuyente;

import java.time.Instant;
import java.util.UUID;

/** Consulta de un RUC en el padrón reducido de SUNAT (#361). */
public interface ConsultarPadronRucUseCase {
    /**
     * @param actualizadoAl cuándo terminó la carga del padrón que respondió: un RUC creado después no aparece, y quien consulta debe poder saberlo.
     */
    record Consulta(Contribuyente contribuyente, Instant actualizadoAl) { }

    /**
     * {@code PARAMETRO_INVALIDO} si no es un RUC (11 dígitos, prefijo y dígito verificador); {@code PADRON_NO_CARGADO} si el padrón nunca se cargó (no se puede afirmar que el
     * RUC no existe); {@code DEMASIADAS_CONSULTAS} si la empresa pasó su cupo; {@code NO_ENCONTRADO} si el RUC no está en el padrón.
     */
    Consulta consultar(UUID tenantId, String ruc);
}
