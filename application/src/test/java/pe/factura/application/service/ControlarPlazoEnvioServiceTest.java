package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ControlarPlazoEnvioServiceTest {
    UUID tenantId = UUID.randomUUID();
    Fakes.Storage storage = new Fakes.Storage();
    Clock unMesDespues = Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneId.of("America/Lima"));

    /**
     * LOG-01: el barrido lee, decide y guarda. Si entre la lectura y el guardado otro proceso resolvió el comprobante (un CDR recuperado lo dejó ACEPTADO), el guardado
     * condicional no lo pisa con FUERA_DE_PLAZO, y ese conflicto no corta el barrido de los demás.
     */
    @Test void noPisaLoQueOtroResolvioEntreLaLecturaYElGuardadoYSigueConLosDemas() {
        Comprobante resuelto = Fakes.facturaFirmada(tenantId, storage, 1);
        Comprobante vencido = Fakes.facturaFirmada(tenantId, storage, 2);
        // La copia que el barrido leyó, antes de que otro proceso persistiera el ACEPTADO
        Comprobante copiaVieja = Comprobante.persistido(resuelto.id(), tenantId, TipoDocumento.FACTURA, "F001", 1L, LocalDate.of(2026, 9, 13), EstadoDocumento.FIRMADO,
                resuelto.receptor(), resuelto.items()).firma("hash", resuelto.nombreArchivo(), resuelto.xmlKey()).envio(0, null).rehidratar();
        Fakes.Comprobantes comprobantes = new Fakes.Comprobantes() {
            @Override public List<Comprobante> pendientesQueVencenEmitidosHasta(LocalDate fecha) { return List.of(copiaVieja, datos.get(vencido.id())); }
        };
        comprobantes.guardar(resuelto);
        comprobantes.guardar(vencido);
        resuelto.marcarEnviado();
        resuelto.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k/cdr.zip");
        comprobantes.guardar(resuelto);

        List<Comprobante> marcados = new ControlarPlazoEnvioService(comprobantes, Fakes.UOW, unMesDespues).marcarVencidos();

        assertThat(marcados).extracting(Comprobante::id).containsExactly(vencido.id());
        assertThat(comprobantes.datos.get(resuelto.id()).estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(comprobantes.datos.get(vencido.id()).estado()).isEqualTo(EstadoDocumento.FUERA_DE_PLAZO);
    }
}
