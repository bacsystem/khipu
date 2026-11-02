package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PepperDeApiKeysRepository;
import pe.factura.application.port.out.SecretosCifradosRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** S2: al arrancar, lo que queda de una rotación de claves se termina de hacer y se dice cuánto falta. */
class RevisarRotacionDeClavesServiceTest {
    int pendientesMasterKey = 3;
    final List<String> huellasCompletadas = new ArrayList<>();
    int activasConOtraHuella = 0;
    String huellaConsultada;

    final SecretosCifradosRepository secretos = new SecretosCifradosRepository() {
        public int pendientes() { return pendientesMasterKey; }
        public int recifrar() { int n = pendientesMasterKey; pendientesMasterKey = 0; return n; }
    };
    final PepperDeApiKeysRepository pepper = new PepperDeApiKeysRepository() {
        public boolean rehashear(UUID id, String a, String n) { throw new AssertionError("al arrancar no se re-hashea: no se conocen las keys"); }
        public int completarHuellas(String huella) { huellasCompletadas.add(huella); return 0; }
        public int activasConOtraHuella(String huella) { huellaConsultada = huella; return activasConOtraHuella; }
    };

    @Test void recifraLoPendienteYDiceQueNoQuedaNada() {
        var informe = new RevisarRotacionDeClavesService(secretos, pepper, "pepper-nuevo", null).revisar();

        assertThat(informe.secretosRecifrados()).isEqualTo(3);
        assertThat(informe.secretosPendientes()).isZero();
    }

    /** Las keys de antes de este cambio no tienen huella: sin rotación en curso, son del pepper vigente. */
    @Test void sinRotarLasKeysSinHuellaSonDelPepperVigente() {
        new RevisarRotacionDeClavesService(secretos, pepper, "pepper-nuevo", null).revisar();
        assertThat(huellasCompletadas).containsExactly(ApiKeyGenerator.huellaDePepper("pepper-nuevo"));
    }

    /** Rotando, las keys sin huella son de antes de rotar: del pepper anterior. Y se cuentan las que todavía dependen de él. */
    @Test void rotandoLasKeysSinHuellaSonDelPepperAnteriorYSeCuentan() {
        activasConOtraHuella = 7;
        var informe = new RevisarRotacionDeClavesService(secretos, pepper, "pepper-nuevo", "pepper-viejo").revisar();

        assertThat(huellasCompletadas).containsExactly(ApiKeyGenerator.huellaDePepper("pepper-viejo"));
        assertThat(huellaConsultada).isEqualTo(ApiKeyGenerator.huellaDePepper("pepper-nuevo"));
        assertThat(informe.apiKeysConPepperAnterior()).isEqualTo(7);
        assertThat(informe.rotandoPepper()).isTrue();
    }

    @Test void laHuellaNoEsElPepperYSiempreMideDieciseis() {
        String h = ApiKeyGenerator.huellaDePepper("un-pepper-cualquiera");
        assertThat(h).hasSize(16).doesNotContain("pepper").matches("[0-9a-f]{16}");
        assertThat(ApiKeyGenerator.huellaDePepper("otro")).isNotEqualTo(h);
    }
}
