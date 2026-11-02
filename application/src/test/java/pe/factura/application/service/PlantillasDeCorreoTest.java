package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PlantillasRepository.Guardada;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** El texto con que sale un correo (#199): el que se guardó o el de fábrica, y nunca un correo que no sale por culpa de su texto. */
class PlantillasDeCorreoTest {
    static final PlantillaDeCorreo RECUPERACION = PlantillaDeCorreo.RECUPERACION_CLAVE;
    static final Map<String, String> VALORES = Map.of("enlace", "https://app/restablecer/T", "validez", "1 hora");

    ConfiguracionFake.Plantillas guardadas = new ConfiguracionFake.Plantillas();
    PlantillasDeCorreo plantillas = new PlantillasDeCorreo(guardadas);

    void guarda(PlantillaDeCorreo tipo, String asunto, String cuerpo) { guardadas.filas.put(tipo, new Guardada(new Texto(asunto, cuerpo), Instant.EPOCH)); }

    @Test void sinTextoGuardadoSaleElDeFabricaConSusVariablesLlenas() {
        Texto t = plantillas.de(RECUPERACION, VALORES);

        assertThat(t).isEqualTo(new Texto("Restablecer contraseña", "Para restablecer tu contraseña abre este enlace (válido 1 hora):\nhttps://app/restablecer/T"));
    }

    @Test void conUnTextoGuardadoSaleEseConSusVariablesLlenas() {
        guarda(RECUPERACION, "Tu enlace de khipu", "Hola. Entra a {enlace} dentro de {validez}.");

        assertThat(plantillas.de(RECUPERACION, VALORES)).isEqualTo(new Texto("Tu enlace de khipu", "Hola. Entra a https://app/restablecer/T dentro de 1 hora."));
    }

    @Test void unTextoGuardadoParaUnCorreoNoAfectaALosDemas() {
        guarda(RECUPERACION, "Otro asunto", "Entra a {enlace}");

        assertThat(plantillas.de(PlantillaDeCorreo.VERIFICACION_CORREO, Map.of("enlace", "https://app/verificar/T", "validez", "24 horas")).asunto()).isEqualTo("Verifica tu correo en khipu");
    }

    /** Una versión nueva pudo quitar una variable que el texto guardado usa: el correo de acceso sale igual, con el texto de fábrica. */
    @Test void unTextoGuardadoQueYaNoEsValidoNoImpideQueElCorreoSalga() {
        guarda(RECUPERACION, "Hola", "Entra a {enlace} o a {variable_que_ya_no_existe}");

        assertThat(plantillas.de(RECUPERACION, VALORES)).isEqualTo(new Texto("Restablecer contraseña", "Para restablecer tu contraseña abre este enlace (válido 1 hora):\nhttps://app/restablecer/T"));
    }

    @Test void unTextoGuardadoSinElEnlaceIndispensableTampocoSaleSinEl() {
        guarda(RECUPERACION, "Hola", "Pide otro enlace en el portal");

        assertThat(plantillas.de(RECUPERACION, VALORES).cuerpo()).contains("https://app/restablecer/T");
    }

    @Test void unTextoGuardadoConElAsuntoEnVariasLineasNoSeUsa() {
        guarda(RECUPERACION, "Hola\nBcc: otro@x.pe", "Entra a {enlace}");

        assertThat(plantillas.de(RECUPERACION, VALORES).asunto()).isEqualTo("Restablecer contraseña");
    }

    @Test void elTextoGuardadoSeNormalizaComoAlGuardar() {
        guarda(RECUPERACION, "  Tu enlace  ", "Entra a {enlace}\r\ngracias\r\n");

        assertThat(plantillas.de(RECUPERACION, VALORES)).isEqualTo(new Texto("Tu enlace", "Entra a https://app/restablecer/T\ngracias"));
    }
}
