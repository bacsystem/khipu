package pe.factura.application.service;

import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * El correo que recibe un cliente cuando el backoffice le avisa algo (#197): qué empresa, qué pasa y qué hacer. Acá solo se calculan los valores de cada aviso; el texto sale de
 * {@link PlantillasDeCorreo} (de fábrica o el que un administrador editó, #199).
 */
final class MensajeDeAviso {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private MensajeDeAviso() {}

    /** La plantilla que corresponde a cada motivo. */
    static PlantillaDeCorreo plantilla(MotivoDeAviso motivo) {
        return switch (motivo) {
            case CERTIFICADO_POR_VENCER -> PlantillaDeCorreo.AVISO_CERTIFICADO_POR_VENCER;
            case CERTIFICADO_VENCIDO -> PlantillaDeCorreo.AVISO_CERTIFICADO_VENCIDO;
            case CREDENCIALES_SOL_INVALIDAS -> PlantillaDeCorreo.AVISO_CREDENCIALES_SOL;
        };
    }

    /**
     * {@code vigenteHasta} y {@code dias} solo se usan en los avisos del certificado: {@code dias} es lo que le queda (0 es «vence hoy», negativo es que ya venció).
     * {@code urlPortal} es donde el cliente carga su certificado o sus credenciales.
     */
    static Texto de(PlantillasDeCorreo plantillas, MotivoDeAviso motivo, String razonSocial, String ruc, LocalDate vigenteHasta, Integer dias, String urlPortal) {
        Map<String, String> valores = new HashMap<>();
        valores.put("razon_social", razonSocial);
        valores.put("ruc", ruc);
        valores.put("enlace", urlPortal);
        if (vigenteHasta != null) valores.put("fecha", FECHA.format(vigenteHasta));
        if (dias != null) valores.put("cuando", cuando(dias));
        return plantillas.de(plantilla(motivo), valores);
    }

    /** «hoy», «mañana», «ayer», «en 10 días» o «hace 5 días». */
    static String cuando(int dias) {
        if (dias == 0) return "hoy";
        if (dias == 1) return "mañana";
        if (dias == -1) return "ayer";
        return dias > 0 ? "en " + dias + " días" : "hace " + -dias + " días";
    }
}
