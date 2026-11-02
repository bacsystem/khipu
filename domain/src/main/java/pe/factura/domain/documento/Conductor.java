package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.Set;

/** Conductor del traslado ({@code cac:DriverPerson}). El primero de la guía es el principal y los siguientes los secundarios (hasta dos, regla 4376). */
public record Conductor(String tipoDoc, String numDoc, String nombres, String apellidos, String licencia) {
    /** Documentos de una persona del catálogo 06: el RUC (6) y el «no domiciliado sin RUC» (0) no identifican a un conductor. */
    static final Set<String> DOCUMENTOS = Set.of("1", "4", "7", "A", "B", "C", "D", "E", "F", "G", "H");

    public Conductor {
        tipoDoc = TextoDeGuia.limpio(tipoDoc);
        numDoc = numDoc == null ? null : numDoc.strip().toUpperCase(java.util.Locale.ROOT);
        nombres = TextoDeGuia.limpio(nombres);
        apellidos = TextoDeGuia.limpio(apellidos);
        licencia = TextoDeGuia.compacto(licencia);
    }

    void exigirValido(int orden) {
        String c = orden == 0 ? "del conductor principal" : "del conductor secundario " + orden;
        if (tipoDoc == null || !DOCUMENTOS.contains(tipoDoc))
            throw new DomainException("TRANSPORTE_INVALIDO", "2571 - El tipo de documento " + c + " es del catálogo 06 (1 DNI, 4 carné de extranjería, 7 pasaporte…); recibido " + tipoDoc);
        if ("1".equals(tipoDoc) ? numDoc == null || !numDoc.matches("\\d{8}") : numDoc == null || !numDoc.matches("[A-Z0-9]{1,15}"))
            throw new DomainException("TRANSPORTE_INVALIDO", "2569 - El documento " + c + " no cumple el formato (DNI de 8 dígitos; los demás hasta 15 letras o dígitos)");
        if (!TextoDeGuia.unaLinea(nombres, 1, 250)) throw new DomainException("TRANSPORTE_INVALIDO", "3360 - Faltan los nombres " + c + " (hasta 250 caracteres, sin saltos de línea)");
        if (!TextoDeGuia.unaLinea(apellidos, 1, 250)) throw new DomainException("TRANSPORTE_INVALIDO", "3361 - Faltan los apellidos " + c + " (hasta 250 caracteres, sin saltos de línea)");
        if (licencia == null) throw new DomainException("TRANSPORTE_INVALIDO", "2572 - Falta el número de licencia de conducir " + c);
        if (!licencia.matches("[A-Z0-9]{9,10}"))
            throw new DomainException("TRANSPORTE_INVALIDO", "2573 - La licencia de conducir " + c + " son de 9 a 10 letras o dígitos (p. ej. Q12345678); recibido " + licencia);
    }

    /** La misma persona: dos conductores con el mismo documento (regla 3362). */
    boolean mismaPersonaQue(Conductor otro) { return tipoDoc.equals(otro.tipoDoc) && numDoc.equals(otro.numDoc); }
}
