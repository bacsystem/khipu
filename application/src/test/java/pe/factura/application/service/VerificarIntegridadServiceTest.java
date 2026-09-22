package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.VerificarIntegridadUseCase.Informe;
import pe.factura.application.port.in.VerificarIntegridadUseCase.Problema;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integridad del storage (#38): XML presente con su DigestValue, CDR presente si SUNAT lo emitió; solo lee. */
class VerificarIntegridadServiceTest {
    Fakes.Comprobantes comprobantes = new Fakes.Comprobantes();
    Fakes.Storage storage = new Fakes.Storage();
    VerificarIntegridadService service = new VerificarIntegridadService(comprobantes, storage);
    UUID tenantId = UUID.randomUUID();
    LocalDate dia = LocalDate.of(2026, 9, 13);

    private Comprobante firmado(String hash, String xml) {
        Comprobante c = Fakes.facturaFirmada(tenantId, storage);
        String key = "k/" + hash + "/" + c.nombreArchivo() + ".xml";   // una clave por comprobante: todos son F001-1
        storage.guardar(key, xml.getBytes());
        Comprobante conHash = Comprobante.persistido(c.id(), tenantId, c.tipo(), c.serie(), c.numero(), c.fechaEmision(), c.estado(), c.receptor(), c.items())
                .firma(hash, c.nombreArchivo(), key).rehidratar();
        comprobantes.guardar(conHash);
        return conHash;
    }

    @Test void todoEnOrden() {
        firmado("abc=", "<Invoice><ds:DigestValue>abc=</ds:DigestValue></Invoice>");
        Informe i = service.verificar(dia, dia);
        assertThat(i.verificados()).isEqualTo(1);
        assertThat(i.limpio()).isTrue();
    }

    @Test void detectaXmlFaltanteCorruptoYCdrFaltante() {
        Comprobante sinXml = firmado("h1=", "<x/>");
        storage.borrar(sinXml.xmlKey());
        firmado("h2=", "<Invoice><ds:DigestValue>otro=</ds:DigestValue></Invoice>");
        firmado("h3=", "<Invoice><ds:DigestValue>h3=</ds:DigestValue></Invoi");
        Comprobante sinCdr = firmado("h4=", "<Invoice><ds:DigestValue>h4=</ds:DigestValue></Invoice>");
        sinCdr.marcarEnviado();
        sinCdr.aplicarCdr(new Cdr("0", "Aceptada", List.of()), "k/R-x.zip");
        comprobantes.guardar(sinCdr);

        Informe i = service.verificar(dia, dia);
        assertThat(i.verificados()).isEqualTo(4);
        assertThat(i.problemas()).extracting(Problema::tipo).containsExactlyInAnyOrder("XML_FALTANTE", "XML_CORRUPTO", "XML_CORRUPTO", "CDR_FALTANTE");
        assertThat(i.problemas()).filteredOn(p -> p.tipo().equals("CDR_FALTANTE")).first().satisfies(p -> {
            assertThat(p.comprobanteId()).isEqualTo(sinCdr.id());
            assertThat(p.detalle()).isEqualTo("k/R-x.zip");
        });
        assertThat(i.problemas()).filteredOn(p -> p.detalle().contains("truncado")).hasSize(1);
    }

    @Test void fueraDelRangoNoSeRevisa() {
        firmado("h=", "<Invoice><ds:DigestValue>h=</ds:DigestValue></Invoice>");
        assertThat(service.verificar(dia.plusDays(1), dia.plusDays(2)).verificados()).isZero();
        assertThatThrownBy(() -> service.verificar(dia, dia.minusDays(1))).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("RANGO_INVALIDO");
    }

    @Test void unStorageCaidoSeInformaSinAbortar() {
        firmado("h=", "<Invoice><ds:DigestValue>h=</ds:DigestValue></Invoice>");
        VerificarIntegridadService roto = new VerificarIntegridadService(comprobantes, new Fakes.Storage() {
            @Override public boolean existe(String k) { throw new IllegalStateException("S3 no responde"); }
        });
        Informe i = roto.verificar(dia, dia);
        assertThat(i.problemas()).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo("STORAGE_INACCESIBLE");
            assertThat(p.detalle()).contains("S3 no responde");
        });
    }
}
