package pe.factura.application.service;

import pe.factura.domain.plataforma.MotivoDeAviso;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * El correo que recibe un cliente cuando el backoffice le avisa algo (#197): qué empresa, qué pasa y qué hacer. Texto plano, sin datos que no aplican. Hoy el texto vive acá;
 * cuando las plantillas sean editables (#199), solo cambia de dónde sale.
 */
public record MensajeDeAviso(String asunto, String cuerpo) {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /**
     * {@code vigenteHasta} y {@code dias} solo se usan en los avisos del certificado: {@code dias} es lo que le queda (0 es «vence hoy»); en un vencido no se menciona.
     * {@code urlPortal} es donde el cliente carga su certificado o sus credenciales.
     */
    public static MensajeDeAviso de(MotivoDeAviso motivo, String razonSocial, String ruc, LocalDate vigenteHasta, Integer dias, String urlPortal) {
        String empresa = razonSocial + " (RUC " + ruc + ")";
        return switch (motivo) {
            case CERTIFICADO_POR_VENCER -> {
                String cuando = dias == 0 ? "hoy" : dias == 1 ? "mañana" : "en " + dias + " días";
                yield new MensajeDeAviso("Tu certificado digital de " + razonSocial + " vence " + cuando,
                        "Hola,\n\nEl certificado digital de " + empresa + " vence el " + FECHA.format(vigenteHasta) + " (" + cuando + "). Cuando venza, khipu ya no podrá firmar "
                                + "los comprobantes de esta empresa y no podrás emitir.\n\nPara renovarlo, consigue un certificado nuevo y cárgalo en el portal:\n" + urlPortal
                                + "\n\nSi ya lo renovaste, ignora este mensaje.\n\nkhipu");
            }
            case CERTIFICADO_VENCIDO -> new MensajeDeAviso("El certificado digital de " + razonSocial + " venció",
                    "Hola,\n\nEl certificado digital de " + empresa + " venció el " + FECHA.format(vigenteHasta) + ". Mientras no cargues uno vigente, khipu no puede firmar "
                            + "los comprobantes de esta empresa y no podrás emitir.\n\nConsigue un certificado nuevo y cárgalo en el portal:\n" + urlPortal + "\n\nkhipu");
            case CREDENCIALES_SOL_INVALIDAS -> new MensajeDeAviso("SUNAT no acepta las credenciales SOL de " + razonSocial,
                    "Hola,\n\nLos envíos de " + empresa + " a SUNAT están fallando porque SUNAT no acepta el usuario o la clave SOL cargados. Los comprobantes quedan pendientes "
                            + "hasta que se corrijan.\n\nRevísalos y vuelve a guardarlos en el portal:\n" + urlPortal + "\n\nSi ya las corregiste, ignora este mensaje.\n\nkhipu");
        };
    }
}
