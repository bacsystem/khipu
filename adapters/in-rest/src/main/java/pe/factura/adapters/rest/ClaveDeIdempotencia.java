package pe.factura.adapters.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.domain.DomainException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** La cabecera {@code Idempotency-Key} de los endpoints que la aceptan (#115, #219): su formato y la huella del pedido. */
final class ClaveDeIdempotencia {
    static final String CABECERA = "Idempotency-Key";
    /** Un UUID es lo recomendado; se admite cualquier texto de 8 a 100 letras, dígitos, guiones o guiones bajos. */
    private static final Pattern VALIDA = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private ClaveDeIdempotencia() {}

    /** {@code null} si el pedido no trae la cabecera: la operación se hace como siempre. */
    static Idempotencia de(String cabecera, Object pedido, ObjectMapper json) {
        if (cabecera == null) return null;
        if (!VALIDA.matcher(cabecera).matches())
            throw new DomainException("IDEMPOTENCIA_INVALIDA", "Idempotency-Key debe tener de 8 a 100 letras, dígitos, guiones o guiones bajos (se recomienda un UUID)");
        return new Idempotencia(cabecera, huella(pedido, json));
    }

    /**
     * SHA-256 del pedido ya interpretado y vuelto a serializar, no de los bytes recibidos: dos JSON con otro espaciado o saltos de
     * línea son el mismo pedido; cambiar un dato da otra huella.
     */
    private static String huella(Object pedido, ObjectMapper json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(pedido)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular la huella del pedido", e);
        }
    }
}
