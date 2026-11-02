package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetalleDeSoporteTest {
    @Test void seEscribeConElUsuarioYLaDuracion() {
        assertThat(new DetalleDeSoporte("ana@negocio.pe", 900).texto()).isEqualTo("usuario=ana@negocio.pe duracion_s=900");
    }

    @Test void loQueSeEscribeSeLee() {
        DetalleDeSoporte original = new DetalleDeSoporte("ana+soporte@negocio.pe", 900);

        assertThat(DetalleDeSoporte.leer(original.texto())).contains(original);
    }

    @Test void unTextoConOtroFormatoNoSeLee() {
        for (String texto : new String[]{null, "", "motivo=se fue", "usuario=ana@negocio.pe", "usuario=ana@negocio.pe duracion_s=", "usuario=ana@negocio.pe duracion_s=abc",
                "usuario=ana@negocio.pe duracion_s=900 extra", " usuario=ana@negocio.pe duracion_s=900", "usuario= duracion_s=900", "usuario=a b duracion_s=900",
                "usuario=ana@negocio.pe duracion_s=99999999999999999999"}) {
            assertThat(DetalleDeSoporte.leer(texto)).as("«%s»", texto).isEmpty();
        }
    }
}
