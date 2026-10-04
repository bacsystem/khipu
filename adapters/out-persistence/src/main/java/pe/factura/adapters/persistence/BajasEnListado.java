package pe.factura.adapters.persistence;

import pe.factura.application.port.in.VisibilidadDeBajas;

/**
 * La única regla de qué hacer con las cuentas dadas de baja (#201) en los listados del backoffice: la usan el de cuentas y el de empresas, así los dos
 * dicen lo mismo cuando se oculta, se incluye o se pide solo las dadas de baja. {@code baja_en} nulo es «en servicio».
 */
final class BajasEnListado {
    private BajasEnListado() {}

    /** Condición sobre una cuenta ya presente en la consulta bajo el alias dado; {@code null} si no hay que filtrar. */
    static String deCuenta(VisibilidadDeBajas bajas, String alias) {
        return switch (bajas) {
            case OCULTAS -> alias + ".baja_en IS NULL";
            case SOLO -> alias + ".baja_en IS NOT NULL";
            case INCLUIDAS -> null;
        };
    }

    /**
     * Condición sobre una empresa (alias {@code t}) según la baja de su cuenta, sin necesitar el {@code JOIN}. Una empresa sin cuenta (de
     * integración) nunca está de baja: se ve al ocultar y no sale al pedir solo las dadas de baja.
     */
    static String deEmpresa(VisibilidadDeBajas bajas) {
        String deBaja = "EXISTS (SELECT 1 FROM cuenta b WHERE b.id = t.cuenta_id AND b.baja_en IS NOT NULL)";
        return switch (bajas) {
            case OCULTAS -> "NOT " + deBaja;
            case SOLO -> deBaja;
            case INCLUIDAS -> null;
        };
    }
}
