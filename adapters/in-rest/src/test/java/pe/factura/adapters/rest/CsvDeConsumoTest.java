package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase.Fila;
import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La exportación del consumo (#193) se abre en una hoja de cálculo: el texto de un cliente no puede ejecutarse como fórmula ni romper las columnas. */
class CsvDeConsumoTest {
    static final UUID CUENTA = UUID.fromString("9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d");
    static final Instant VENCE = Instant.parse("2026-10-20T05:00:00Z");

    static Fila fila(String nombre, String email, String plan) {
        return new Fila(CUENTA, nombre, email, UUID.randomUUID(), plan, 240, Limite.de(300), 80, true, EstadoSuscripcion.EN_GRACIA, VENCE, 5, VENCE.plusSeconds(5 * 86_400));
    }

    static String csv(Fila... filas) { return CsvDeConsumo.de(List.of(filas)); }

    static List<String> lineas(String csv) { return List.of(csv.substring(1).split("\r\n", -1)); }

    @Test void empiezaConLaMarcaUtf8ParaQueLaHojaDeCalculoLeaLasTildes() {
        assertThat(csv()).startsWith("﻿");
        assertThat(csv().getBytes(StandardCharsets.UTF_8)).startsWith(0xEF, 0xBB, 0xBF);
    }

    @Test void laCabeceraDiceLasColumnasEnElOrdenDeLasFilas() {
        assertThat(lineas(csv()).get(0)).isEqualTo("cuenta_id,cuenta,correo,plan,documentos,limite,porcentaje,en_alerta,estado_del_plan,pagado_hasta,se_sirve_hasta");
    }

    @Test void unaFilaLlevaTodosSusDatos() {
        String l = lineas(csv(fila("Ana Quispe", "ana@negocio.pe", "Emprende"))).get(1);

        assertThat(l).isEqualTo("9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d,Ana Quispe,ana@negocio.pe,Emprende,240,300,80,si,EN_GRACIA,2026-10-20T05:00:00Z,2026-10-25T05:00:00Z");
    }

    @Test void terminaCadaRegistroConCrlfIncluidoElUltimo() {
        String s = csv(fila("A", "a@x.pe", "P"), fila("B", "b@x.pe", "P"));

        assertThat(s).endsWith("\r\n");
        assertThat(s.split("\r\n")).hasSize(3);
        assertThat(s.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
    }

    @Test void unPlanSinTopeDiceIlimitadoYNoTienePorcentaje() {
        Fila f = new Fila(CUENTA, "Ana", "a@x.pe", UUID.randomUUID(), "Pro", 9000, Limite.sinLimite(), null, false, EstadoSuscripcion.VIGENTE, null, 0, null);

        assertThat(lineas(csv(f)).get(1)).isEqualTo("9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d,Ana,a@x.pe,Pro,9000,ilimitado,,no,VIGENTE,,");
    }

    @Test void unaCeldaConComaComillasOSaltosVaEntreComillasConLasComillasDuplicadas() {
        String s = csv(fila("Pérez, \"El Sol\" SAC", "a@x.pe", "Plan\nUno"));

        assertThat(s).contains(",\"Pérez, \"\"El Sol\"\" SAC\",").contains(",\"Plan\nUno\",");
    }

    @Test void unNombreConSaltoDeLineaNoPartePorLoMenosLasColumnas() {
        String l = csv(fila("Ana\r\nInyectada,x", "a@x.pe", "P"));

        assertThat(l).contains("\"Ana\r\nInyectada,x\"");
    }

    /** Un retorno de carro suelto (sin salto de línea detrás) también parte el registro en algunos lectores: va entre comillas. */
    @Test void unRetornoDeCarroSueltoVaEntreComillas() {
        assertThat(csv(fila("Ana\rLuis", "a@x.pe", "P"))).contains(",\"Ana\rLuis\",");
    }

    /** Excel y Calc evalúan como fórmula una celda que empieza por = + - @ (o por tab o retorno): se le antepone una comilla para que sea texto. */
    @Test void unaCeldaQueEmpiezaComoFormulaSeNeutraliza() {
        for (String peligro : List.of("=HYPERLINK(\"http://x\")", "+cmd|' /C calc'!A0", "-2+3", "@SUM(A1)", "\tcmd", "\rcmd")) {
            String s = csv(fila(peligro, "a@x.pe", "P"));
            String celda = s.split("\r\n", -1)[1].split(",", 3)[1].replaceFirst("^\"", "");

            assertThat(celda).as(peligro).startsWith("'");
        }
    }

    @Test void laNeutralizacionAlcanzaElCorreoYElPlanYNoTocaLoQueEmpiezaConOtraCosa() {
        String s = csv(fila("Ana", "=a@x.pe", "@Plan"), fila("Luis=1", "luis@x.pe", "Plan+"));

        assertThat(lineas(s).get(1)).contains(",'=a@x.pe,'@Plan,");
        assertThat(lineas(s).get(2)).contains(",Luis=1,luis@x.pe,Plan+,");
    }

    @Test void unaCeldaConFormulaYConComaSeNeutralizaYSeEntrecomilla() {
        String s = csv(fila("=1,2", "a@x.pe", "P"));

        assertThat(lineas(s).get(1)).contains(",\"'=1,2\",");
    }

    @Test void elNumeroNegativoDeLasColumnasNumericasNoSeToca() {
        Fila f = new Fila(CUENTA, "Ana", "a@x.pe", UUID.randomUUID(), "P", 0, Limite.de(1), 0, false, EstadoSuscripcion.VIGENTE, null, 0, null);

        assertThat(lineas(csv(f)).get(1)).contains(",0,1,0,no,");
    }
}
