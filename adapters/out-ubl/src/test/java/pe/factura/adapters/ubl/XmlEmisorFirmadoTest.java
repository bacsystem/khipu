package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El lector se prueba contra el XML que genera el propio sistema, no contra una cadena escrita a mano: así, si la
 * plantilla mueve el emisor de lugar, el test se entera.
 */
class XmlEmisorFirmadoTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    final XmlEmisorFirmado lector = new XmlEmisorFirmado();

    static Tenant tenant(Domicilio domicilio, String nombreComercial) {
        return new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.BETA, null,
                new CertificadoDigital(new byte[0], "", LocalDate.of(2030, 1, 1)), domicilio, null, nombreComercial);
    }

    static Comprobante factura() {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", "AV. LIMA 123"),
                List.of(new Item("P001", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("2360.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        c.asignarNumero(1, "20100066603");
        return c;
    }

    static byte[] xmlDe(Tenant t) {
        return new FreemarkerUblGenerator().generar(factura(), t).getBytes(StandardCharsets.UTF_8);
    }

    @Test void leeLaIdentidadCompletaDelEmisor() {
        Domicilio dom = new Domicilio("150122", "Av. Larco 345 Of. 12", "Urb. Santa Cruz", null, null, null, "0002");

        EmisorImpreso e = lector.leer(xmlDe(tenant(dom, "Andina Store"))).orElseThrow();

        assertThat(e.ruc()).isEqualTo("20100066603");
        assertThat(e.razonSocial()).isEqualTo("EMPRESA DE PRUEBA S.A.C.");
        assertThat(e.nombreComercial()).isEqualTo("Andina Store");
        assertThat(e.domicilio().ubigeo()).isEqualTo("150122");
        assertThat(e.domicilio().direccion()).isEqualTo("Av. Larco 345 Of. 12");
        assertThat(e.domicilio().urbanizacion()).isEqualTo("Urb. Santa Cruz");
        assertThat(e.domicilio().distrito()).isEqualTo("MIRAFLORES");
        assertThat(e.domicilio().provincia()).isEqualTo("LIMA");
        assertThat(e.domicilio().departamento()).isEqualTo("LIMA");
        assertThat(e.domicilio().codigoEstablecimiento()).isEqualTo("0002");
    }

    /**
     * El receptor tiene la misma estructura que el emisor: razón social, dirección, ubigeo. Buscar por nombre de tag en
     * el documento entero traería sus datos, y la impresa saldría con el nombre del cliente como emisor.
     */
    @Test void noSeConfundeConElReceptor() {
        EmisorImpreso e = lector.leer(xmlDe(tenant(Domicilio.de("150122", "Av. Larco 345"), null))).orElseThrow();

        assertThat(e.razonSocial()).isEqualTo("EMPRESA DE PRUEBA S.A.C.").isNotEqualTo("CLIENTE SAC");
        assertThat(e.ruc()).isEqualTo("20100066603").isNotEqualTo("20601234565");
        assertThat(e.domicilio().direccion()).isEqualTo("Av. Larco 345").isNotEqualTo("AV. LIMA 123");
    }

    @Test void sinNombreComercialLoDevuelveNulo() {
        assertThat(lector.leer(xmlDe(tenant(Domicilio.de("150122", "Av. Larco 345"), null))).orElseThrow().nombreComercial()).isNull();
    }

    /** Sin domicilio configurado el XML lleva solo el AddressTypeCode: no hay ubigeo con el que reconstruirlo. */
    @Test void sinDomicilioEnElXmlDevuelveLaIdentidadSinDomicilio() {
        EmisorImpreso e = lector.leer(xmlDe(tenant(null, null))).orElseThrow();
        assertThat(e.razonSocial()).isEqualTo("EMPRESA DE PRUEBA S.A.C.");
        assertThat(e.domicilio()).isNull();
    }

    @Test void loQueNoSePuedeLeerNoRompeLaImpresion() {
        assertThat(lector.leer(null)).isEmpty();
        assertThat(lector.leer(new byte[0])).isEmpty();
        assertThat(lector.leer("no soy xml".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(lector.leer("<Invoice/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    /**
     * Un XML con DOCTYPE no se procesa, ni siquiera cuando el resto es un emisor perfectamente válido. El bloque del
     * emisor está completo a propósito: si el parser aceptara la declaración, la lectura saldría bien y la entidad
     * quedaría expandida en la razón social. Que devuelva vacío es lo que prueba que el DOCTYPE se rechaza antes.
     */
    @Test void rechazaCualquierXmlConDoctype_aunqueElEmisorSeaValido() {
        String conDoctype = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Invoice [<!ENTITY inyectada "ESTO NO DEBERIA EXPANDIRSE">]>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cac:AccountingSupplierParty><cac:Party>
                    <cac:PartyIdentification><cbc:ID>20100066603</cbc:ID></cac:PartyIdentification>
                    <cac:PartyLegalEntity><cbc:RegistrationName>&inyectada;</cbc:RegistrationName></cac:PartyLegalEntity>
                  </cac:Party></cac:AccountingSupplierParty>
                </Invoice>
                """;
        assertThat(lector.leer(conDoctype.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    /** El mismo XML sin DOCTYPE sí se lee: así se sabe que lo que lo rechaza es la declaración y no otra cosa. */
    @Test void elMismoXmlSinDoctypeSiSeLee() {
        String sinDoctype = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cac:AccountingSupplierParty><cac:Party>
                    <cac:PartyIdentification><cbc:ID>20100066603</cbc:ID></cac:PartyIdentification>
                    <cac:PartyLegalEntity><cbc:RegistrationName>EMPRESA SIN DOCTYPE</cbc:RegistrationName></cac:PartyLegalEntity>
                  </cac:Party></cac:AccountingSupplierParty>
                </Invoice>
                """;
        assertThat(lector.leer(sinDoctype.getBytes(StandardCharsets.UTF_8)).orElseThrow().razonSocial()).isEqualTo("EMPRESA SIN DOCTYPE");
    }
}
