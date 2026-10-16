package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PadronRucRepository.Contribuyente;

import static org.assertj.core.api.Assertions.assertThat;

/** #361: una línea del archivo del padrón reducido de SUNAT, tal como viene (15 campos, un «|» final, «-» donde no hay dato). */
class FormatoDelPadronTest {
    @Test void unaEmpresaConDireccionSeArmaComoLaFichaRuc() {
        Contribuyente c = FormatoDelPadron.parsear("20330791412|ORYGEN PERU S.A.A.|ACTIVO|HABIDO|150130|----|PASEO DEL BOSQUE|URB.|CHACARILLA DEL ESTANQUE|500|-|-|-|-|-|").orElseThrow();

        assertThat(c.ruc()).isEqualTo("20330791412");
        assertThat(c.razonSocial()).isEqualTo("ORYGEN PERU S.A.A.");
        assertThat(c.estado()).isEqualTo("ACTIVO");
        assertThat(c.condicion()).isEqualTo("HABIDO");
        assertThat(c.ubigeo()).isEqualTo("150130");
        assertThat(c.direccion()).isEqualTo("PASEO DEL BOSQUE NRO. 500 URB. CHACARILLA DEL ESTANQUE");
        assertThat(c.activo()).isTrue();
        assertThat(c.habido()).isTrue();
    }

    @Test void todasLasPartesDeLaDireccionVanEnSuLugar() {
        Contribuyente c = FormatoDelPadron.parsear("20100193117|YARA PERU S.R.L.|ACTIVO|HABIDO|150140|JR.|MONTERROSA|URB.|LOS ROSALES|271|3|7|402|B|12|").orElseThrow();

        assertThat(c.direccion()).isEqualTo("JR. MONTERROSA NRO. 271 INT. 3 DPTO. 402 MZA. B LOTE 7 KM. 12 URB. LOS ROSALES");
    }

    @Test void unRucDePersonaNaturalSinDomicilioNoTraeUbigeoNiDireccion() {
        Contribuyente c = FormatoDelPadron.parsear("10452159428|GARCIA CHANCO CARLOS AUGUSTO|ACTIVO|HABIDO|-|-|-|-|-|-|-|-|-|-|-|").orElseThrow();

        assertThat(c.ubigeo()).isNull();
        assertThat(c.direccion()).isNull();
        assertThat(c.razonSocial()).isEqualTo("GARCIA CHANCO CARLOS AUGUSTO");
    }

    @Test void losEstadosDeBajaNoSonActivosNiHabidos() {
        Contribuyente c = FormatoDelPadron.parsear("20160109441|GARAMENDI ALARCON ANTONIO|BAJA DE OFICIO|NO HABIDO|-|-|-|-|-|-|-|-|-|-|-|").orElseThrow();

        assertThat(c.estado()).isEqualTo("BAJA DE OFICIO");
        assertThat(c.activo()).isFalse();
        // «NO HABIDO» contiene «HABIDO»: se compara el texto entero.
        assertThat(c.habido()).isFalse();
    }

    @Test void unaBarraDentroDeUnCampoNoCorreLasColumnas() {
        // Filas reales del archivo: el «|» del nombre y el del campo de la manzana vienen escapados con «\».
        Contribuyente nombre = FormatoDelPadron.parsear("20614244730|EBEN EZER \\| INGENIERIA Y ARQUITECTURA S.A.C.|ACTIVO|HABIDO|150114|CAL.|LA PAZ|URB.|SANTA PATRICIA ET. UNO|200|-|-|-|-|-|").orElseThrow();
        assertThat(nombre.razonSocial()).isEqualTo("EBEN EZER | INGENIERIA Y ARQUITECTURA S.A.C.");
        assertThat(nombre.estado()).isEqualTo("ACTIVO");
        assertThat(nombre.condicion()).isEqualTo("HABIDO");

        Contribuyente manzana = FormatoDelPadron.parsear("20509271055|ARTE,TRADICION Y CULTURA S.R.L.|BAJA DE OFICIO|NO HABIDO|150132|-|-|----|ASOC. GARAGAY|-|-|28|-|O-1\\||-|").orElseThrow();
        assertThat(manzana.estado()).isEqualTo("BAJA DE OFICIO");
        assertThat(manzana.direccion()).isEqualTo("MZA. O-1| LOTE 28 ASOC. GARAGAY");
    }

    @Test void sinLaBarraFinalTambienSeLee() {
        assertThat(FormatoDelPadron.parsear("10452159428|GARCIA|ACTIVO|HABIDO|-|-|-|-|-|-|-|-|-|-|-")).isPresent();
    }

    @Test void unaLineaConMasOMenosCamposSeDescarta() {
        assertThat(FormatoDelPadron.parsear("10452159428|GARCIA|ACTIVO|HABIDO|")).as("corta").isEmpty();
        assertThat(FormatoDelPadron.parsear("10452159428|GARCIA|ACTIVO|HABIDO|-|-|-|-|-|-|-|-|-|-|-|-|-|")).as("larga").isEmpty();
        assertThat(FormatoDelPadron.parsear("")).isEmpty();
    }

    @Test void unRucQueNoSonOnceDigitosSeDescarta() {
        assertThat(FormatoDelPadron.parsear("1045215942|GARCIA|ACTIVO|HABIDO|-|-|-|-|-|-|-|-|-|-|-|")).as("diez").isEmpty();
        assertThat(FormatoDelPadron.parsear("1045215942A|GARCIA|ACTIVO|HABIDO|-|-|-|-|-|-|-|-|-|-|-|")).as("letra").isEmpty();
        assertThat(FormatoDelPadron.parsear("RUC|NOMBRE O RAZÓN SOCIAL|ESTADO DEL CONTRIBUYENTE|CONDICIÓN DE DOMICILIO|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO|"))
                .as("el encabezado").isEmpty();
    }

    @Test void elEncabezadoSeReconoce() {
        assertThat(FormatoDelPadron.esEncabezado("RUC|NOMBRE O RAZÓN SOCIAL|")).isTrue();
        assertThat(FormatoDelPadron.esEncabezado("20100066603|TIENDAS|")).isFalse();
    }

    @Test void unUbigeoQueNoSonSeisDigitosSeIgnora() {
        assertThat(FormatoDelPadron.parsear("20100066603|TIENDAS|ACTIVO|HABIDO|15013|-|-|-|-|-|-|-|-|-|-|").orElseThrow().ubigeo()).isNull();
    }
}
