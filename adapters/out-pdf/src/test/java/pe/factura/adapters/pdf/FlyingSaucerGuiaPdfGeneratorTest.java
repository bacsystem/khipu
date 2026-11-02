package pe.factura.adapters.pdf;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** #39: la representación impresa de la guía de remisión lleva el traslado completo y el QR solo cuando SUNAT dio la URL de consulta en el CDR. */
class FlyingSaucerGuiaPdfGeneratorTest {
    final FlyingSaucerGuiaPdfGenerator generador = new FlyingSaucerGuiaPdfGenerator();

    static Traslado traslado(List<Vehiculo> v, List<Conductor> c) {
        return new Traslado(LocalDate.of(2026, 9, 14), new BigDecimal("1250.5"), "KGM", null, new PuntoDeTraslado("150101", "Av. Argentina 1234", "0001", null),
                PuntoDeTraslado.de("040101", "Calle Mercaderes 200, Arequipa"), v, c, Set.of(IndicadorDeTraslado.RETORNO_VEHICULO_VACIO));
    }

    static GuiaRemision remitente() {
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), "20100066603", "T001", LocalDate.of(2026, 9, 13), new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null),
                traslado(List.of(new Vehiculo("ABC123"), new Vehiculo("XYZ987")), List.of(new Conductor("1", "45678912", "JUAN", "QUISPE MAMANI", "Q45678912"))),
                List.of(new ItemDeGuia("LAP-15", "Laptop 15 pulgadas", "NIU", new BigDecimal("2"), null), new ItemDeGuia(null, "Café", "KGM", new BigDecimal("12.375"), null)),
                List.of(new DocumentoDeTraslado("01", "F001-123", "20100066603")), "Frágil", FlyingSaucerPdfGeneratorTest.CLOCK);
        g.asignarNumero(15, "20100066603");
        g.firmar("hashGuia==", "k.xml");
        return g;
    }

    @Test void laGuiaRemitenteMuestraElTrasladoYSinCdrNoLlevaQr() {
        String html = generador.xhtml(remitente(), FlyingSaucerPdfGeneratorTest.TENANT, null, null);
        assertThat(html).contains("GUÍA DE REMISIÓN ELECTRÓNICA REMITENTE", "T001-15", "R.U.C. 20100066603", "COMERCIAL ANDINA S.A.C.", "13/09/2026", "14/09/2026",
                "01 - Venta", "02 - Transporte privado", "1,250.5 KGM", "Av. Argentina 1234", "establecimiento 0001 (RUC 20100066603)", "Calle Mercaderes 200, Arequipa",
                "Vehículo principal", "Placa ABC123", "Vehículo secundario", "Placa XYZ987", "Conductor principal", "JUAN QUISPE MAMANI", "licencia Q45678912",
                "retorno_vehiculo_vacio", "Laptop 15 pulgadas", "12.375", "Factura", "F001-123", "Frágil", "hashGuia==", "aún no devolvió la constancia");
        assertThat(html).doesNotContain("alt=\"QR\"").doesNotContain("Remitente</h2>");
    }

    @Test void laGuiaTransportistaMuestraAlRemitenteYConCdrLlevaElQr() throws Exception {
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), "20100066603", "V001", LocalDate.of(2026, 9, 13), new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeTransportista(new ParteDeGuia("6", "20555555556", "DISTRIBUIDORA NORTE S.A."), "1512345CNG"),
                new Traslado(LocalDate.of(2026, 9, 14), BigDecimal.TEN, "TNE", null, PuntoDeTraslado.de("150101", "Av. Argentina 1234"), PuntoDeTraslado.de("040101", "Calle 1"),
                        List.of(new Vehiculo("ABC123")), List.of(new Conductor("1", "45678912", "JUAN", "QUISPE", "Q45678912")), Set.of()),
                List.of(new ItemDeGuia(null, "Cajas", "BX", BigDecimal.TEN, null)), List.of(), null, FlyingSaucerPdfGeneratorTest.CLOCK);
        g.asignarNumero(3, "20100066603");
        g.firmar("h==", "k.xml");
        g.marcarEnviada("t", null);
        g.aplicarCdr(new Cdr("0", "aceptada", List.of()), "r.zip", "https://e-factura.sunat.gob.pe/qr?x=1");
        String html = generador.xhtml(g, FlyingSaucerPdfGeneratorTest.TENANT, g.enlaceConsulta(), null);
        assertThat(html).contains("GUÍA DE REMISIÓN ELECTRÓNICA TRANSPORTISTA", "V001-3", "Remitente", "DISTRIBUIDORA NORTE S.A.", "1512345CNG", "alt=\"QR\"",
                "https://e-factura.sunat.gob.pe/qr?x=1", "10 TNE");
        assertThat(html).doesNotContain("Modalidad").doesNotContain("aún no devolvió");

        byte[] pdf = generador.generar(g, FlyingSaucerPdfGeneratorTest.TENANT, g.enlaceConsulta(), null);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("/Subtype/Image");
    }

    @Test void conTransportePublicoMuestraALaEmpresaDeTransporte() {
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), "20100066603", "T001", LocalDate.of(2026, 9, 13), new ParteDeGuia("1", "45678912", "MARÍA LÓPEZ"),
                new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PUBLICO, new TransportistaContratado("20480072872", "TRANSPORTES DEL SUR S.A.C.", "MTC123")),
                traslado(List.of(), List.of()), List.of(new ItemDeGuia(null, "Cajas", "BX", BigDecimal.TEN, null)), List.of(), null, FlyingSaucerPdfGeneratorTest.CLOCK);
        g.asignarNumero(16, "20100066603");
        g.firmar("h==", "k.xml");
        assertThat(generador.xhtml(g, FlyingSaucerPdfGeneratorTest.TENANT, null, null))
                .contains("01 - Transporte público", "RUC 20480072872 · TRANSPORTES DEL SUR S.A.C. · MTC MTC123", "Documento Nacional de Identidad")
                .doesNotContain("Vehículo principal");
    }
}
