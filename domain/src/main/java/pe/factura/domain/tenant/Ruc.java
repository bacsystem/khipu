package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;

/**
 * RUC de SUNAT: 11 dígitos, empieza por 10 (persona natural), 15, 16, 17 o 20 (persona jurídica) y el último es el dígito
 * verificador (módulo 11 con pesos 5-4-3-2-7-6-5-4-3-2). Comprobarlo localmente evita consumir un número con un RUC mal
 * tipeado que SUNAT rechazaría (2017 en el receptor; el emisor lo valida contra su padrón).
 */
public final class Ruc {
    private Ruc() {}

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    public static boolean formatoValido(String ruc) { return ruc != null && ruc.matches("(10|15|16|17|20)\\d{9}"); }

    public static boolean esValido(String ruc) {
        if (!formatoValido(ruc)) return false;
        int suma = 0;
        for (int i = 0; i < 10; i++) suma += (ruc.charAt(i) - '0') * PESOS[i];
        int resto = 11 - suma % 11;
        int digito = resto == 10 ? 0 : resto == 11 ? 1 : resto;
        return ruc.charAt(10) - '0' == digito;
    }

    public static void exigirValido(String ruc, String codigoError, String contexto) {
        if (!formatoValido(ruc)) throw new DomainException(codigoError, contexto + ": el RUC son 11 dígitos que empiezan por 10, 15, 16, 17 o 20 (" + ruc + ")");
        if (!esValido(ruc)) throw new DomainException(codigoError, contexto + ": el dígito verificador del RUC " + ruc + " no es válido; revise el número");
    }
}
