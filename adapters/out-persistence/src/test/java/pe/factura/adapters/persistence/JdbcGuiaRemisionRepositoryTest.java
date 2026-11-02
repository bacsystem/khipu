package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.GuiaRemisionRepository.Filtro;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Serie;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #39: la guía de remisión se guarda y se lee tal cual (los dos tipos), el envío se actualiza sin pisar una guía resuelta, y los CHECK de V69 cuidan la base. */
class JdbcGuiaRemisionRepositoryTest extends PersistenciaTestBase {
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Lima"));
    static final LocalDate HOY = LocalDate.of(2026, 10, 20);
    static final String EMISOR = "20100066603";
    JdbcGuiaRemisionRepository repo = new JdbcGuiaRemisionRepository(jdbc);

    static Traslado traslado(List<Vehiculo> v, List<Conductor> c, Set<IndicadorDeTraslado> ind) {
        return new Traslado(HOY.plusDays(1), new BigDecimal("1250.5"), "KGM", null, new PuntoDeTraslado("150101", "Av. Argentina 1234", "0001", null),
                PuntoDeTraslado.de("040101", "Calle Mercaderes 200"), v, c, ind);
    }

    static GuiaRemision remitente(UUID tenant, long numero) {
        GuiaRemision g = GuiaRemision.crear(tenant, EMISOR, "T001", HOY, new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null),
                traslado(List.of(new Vehiculo("ABC123"), new Vehiculo("XYZ987")), List.of(new Conductor("1", "45678912", "JUAN", "QUISPE", "Q45678912"),
                        new Conductor("4", "CE001234", "ANA", "RUIZ", "B12345678")), Set.of(IndicadorDeTraslado.RETORNO_VEHICULO_VACIO, IndicadorDeTraslado.TRANSBORDO_PROGRAMADO)),
                List.of(new ItemDeGuia("P001", "Laptop", "NIU", new BigDecimal("2"), "43211503"), new ItemDeGuia(null, "Café", "KGM", new BigDecimal("12.375"), null)),
                List.of(new DocumentoDeTraslado("01", "F001-123", EMISOR), new DocumentoDeTraslado("50", "118-2026-10-123456", null)), "Frágil", RELOJ);
        g.asignarNumero(numero, EMISOR);
        g.firmar("HASH" + numero, "k/" + numero + ".xml");
        return g;
    }

    static GuiaRemision publica(UUID tenant, long numero) {
        GuiaRemision g = GuiaRemision.crear(tenant, EMISOR, "T001", HOY, new ParteDeGuia("1", "45678912", "MARÍA LÓPEZ"),
                new DatosDeGuia.DeRemitente("13", "Traslado a feria", ModalidadTraslado.PUBLICO, new TransportistaContratado("20480072872", "TRANSPORTES DEL SUR S.A.C.", null)),
                traslado(List.of(), List.of(), Set.of()), List.of(new ItemDeGuia(null, "Caja", "BX", BigDecimal.TEN, null)), List.of(), null, RELOJ);
        g.asignarNumero(numero, EMISOR);
        g.firmar("H", "k.xml");
        return g;
    }

    static GuiaRemision transportista(UUID tenant, long numero) {
        GuiaRemision g = GuiaRemision.crear(tenant, EMISOR, "V001", HOY, new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeTransportista(new ParteDeGuia("6", "20555555556", "DISTRIBUIDORA NORTE S.A."), "1512345CNG"),
                traslado(List.of(new Vehiculo("ABC123")), List.of(new Conductor("1", "45678912", "JUAN", "QUISPE", "Q45678912")), Set.of()),
                List.of(new ItemDeGuia(null, "Caja", "BX", BigDecimal.TEN, null)), List.of(new DocumentoDeTraslado("09", "T001-15", "20555555556")), null, RELOJ);
        g.asignarNumero(numero, EMISOR);
        g.firmar("H", "k.xml");
        return g;
    }

    @Test void laGuiaRemitenteSeGuardaYSeLeeTalCual() {
        UUID t = tenantDePrueba();
        GuiaRemision g = remitente(t, 15);
        uow.ejecutar(() -> repo.guardar(g));
        GuiaRemision leida = repo.buscar(t, g.id()).orElseThrow();
        assertThat(leida.tipo()).isEqualTo(TipoDocumento.GUIA_REMITENTE);
        assertThat(leida.serie()).isEqualTo("T001");
        assertThat(leida.numero()).isEqualTo(15);
        assertThat(leida.fechaEmision()).isEqualTo(HOY);
        assertThat(leida.horaEmision()).isEqualTo(g.horaEmision());
        assertThat(leida.estado()).isEqualTo(EstadoGuia.FIRMADO);
        assertThat(leida.destinatario()).isEqualTo(g.destinatario());
        assertThat(leida.datos()).isEqualTo(g.datos());
        assertThat(leida.traslado()).isEqualTo(g.traslado());
        assertThat(leida.items()).isEqualTo(g.items());
        assertThat(leida.items().get(1).cantidad()).isEqualByComparingTo("12.375").hasToString("12.375");
        assertThat(leida.traslado().pesoBruto()).hasToString("1250.5");
        assertThat(leida.documentos()).isEqualTo(g.documentos());
        assertThat(leida.observaciones()).isEqualTo("Frágil");
        assertThat(leida.nombreArchivo()).isEqualTo("20100066603-09-T001-15");
        assertThat(leida.hash()).isEqualTo("HASH15");
        assertThat(leida.xmlKey()).isEqualTo("k/15.xml");
        assertThat(repo.buscar(tenantDePrueba(), g.id())).as("de otra empresa no existe").isEmpty();
    }

    @Test void lasGuiasPublicaYTransportistaSeLeenConSusDatos() {
        UUID t = tenantDePrueba();
        GuiaRemision p = publica(t, 16), tr = transportista(t, 3);
        uow.ejecutar(() -> { repo.guardar(p); repo.guardar(tr); });
        GuiaRemision lp = repo.buscar(t, p.id()).orElseThrow();
        assertThat(lp.datos()).isEqualTo(p.datos());
        assertThat(lp.traslado().vehiculos()).isEmpty();
        assertThat(lp.traslado().indicadores()).isEmpty();
        GuiaRemision lt = repo.buscar(t, tr.id()).orElseThrow();
        assertThat(lt.tipo()).isEqualTo(TipoDocumento.GUIA_TRANSPORTISTA);
        assertThat(lt.datos()).isEqualTo(tr.datos());
        assertThat(lt.traslado().partida().rucEstablecimiento()).isEqualTo("20555555556");
        assertThat(lt.documentos()).isEqualTo(tr.documentos());
    }

    @Test void elEnvioYElTicketSeActualizanYUnaGuiaResueltaNoSePisa() {
        UUID t = tenantDePrueba();
        GuiaRemision g = remitente(t, 1);
        uow.ejecutar(() -> repo.guardar(g));
        g.marcarEnviada("7f7b00ab-4fee-4ae2-9e04-fbd16ca18677", LocalDateTime.of(2026, 10, 20, 10, 0, 1));
        g.ticketEnProceso();
        uow.ejecutar(() -> repo.guardar(g));
        GuiaRemision enCurso = repo.buscar(t, g.id()).orElseThrow();
        assertThat(enCurso.estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(enCurso.ticket()).isEqualTo("7f7b00ab-4fee-4ae2-9e04-fbd16ca18677");
        assertThat(enCurso.fechaRecepcion()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0, 1));
        assertThat(enCurso.consultasTicket()).isEqualTo(1);
        assertThat(enCurso.reintentoAutomatico()).isTrue();
        assertThat(enCurso.intentos()).isEqualTo(1);

        g.aplicarCdr(new Cdr("0", "La Guia numero T001-1, ha sido aceptada", List.of("4000 - obs")), "r.zip", "https://e-factura.sunat.gob.pe/x");
        uow.ejecutar(() -> repo.guardar(g));
        GuiaRemision aceptada = repo.buscar(t, g.id()).orElseThrow();
        assertThat(aceptada.estado()).isEqualTo(EstadoGuia.ACEPTADO_CON_OBS);
        assertThat(aceptada.cdr()).isEqualTo(new Cdr("0", "La Guia numero T001-1, ha sido aceptada", List.of("4000 - obs")));
        assertThat(aceptada.cdrKey()).isEqualTo("r.zip");
        assertThat(aceptada.enlaceConsulta()).isEqualTo("https://e-factura.sunat.gob.pe/x");
        assertThat(aceptada.reintentoAutomatico()).isFalse();

        // Una consulta del ticket que llega tarde (leyó la guía antes de que se resolviera) no la vuelve a «en proceso».
        enCurso.ticketSinRespuesta("0109 - tiempo agotado", true);
        assertThatThrownBy(() -> uow.ejecutar(() -> repo.guardar(enCurso))).isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("codigo", "ESTADO_CONFLICTO");
        assertThat(repo.buscar(t, g.id()).orElseThrow().estado()).isEqualTo(EstadoGuia.ACEPTADO_CON_OBS);
    }

    @Test void seListaYSeCuentaPorEmpresaYFiltros() {
        UUID t = tenantDePrueba();
        GuiaRemision a = remitente(t, 1), b = publica(t, 2), c = transportista(t, 1);
        c.marcarErrorEnvio("0000 - caída", true);
        uow.ejecutar(() -> { repo.guardar(a); repo.guardar(b); repo.guardar(c); });
        UUID otra = tenantDePrueba();
        uow.ejecutar(() -> repo.guardar(remitente(otra, 1)));
        assertThat(repo.contar(t, Filtro.NINGUNO)).isEqualTo(3);
        assertThat(repo.listar(t, Filtro.NINGUNO, 1, 2)).hasSize(2);
        assertThat(repo.listar(t, Filtro.NINGUNO, 2, 2)).hasSize(1);
        assertThat(repo.listar(t, new Filtro(null, TipoDocumento.GUIA_TRANSPORTISTA, null, null, null), 1, 20)).extracting(GuiaRemision::id).containsExactly(c.id());
        assertThat(repo.contar(t, new Filtro(EstadoGuia.ERROR_ENVIO, null, null, null, null))).isEqualTo(1);
        assertThat(repo.contar(t, new Filtro(null, null, "T001", HOY, HOY))).isEqualTo(2);
        assertThat(repo.contar(t, new Filtro(null, null, null, HOY.plusDays(1), null))).isZero();
        assertThat(repo.listar(t, Filtro.NINGUNO, 1, 20)).allSatisfy(g -> assertThat(g.items()).isNotEmpty());
    }

    @Test void laMismaSerieYNumeroNoSeRepiteEnLaEmpresa() {
        UUID t = tenantDePrueba();
        uow.ejecutar(() -> repo.guardar(remitente(t, 1)));
        assertThatThrownBy(() -> uow.ejecutar(() -> repo.guardar(remitente(t, 1)))).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void laSerieDeGuiaSeNumeraComoLasDemas() {
        UUID t = tenantDePrueba();
        JdbcSerieRepository series = new JdbcSerieRepository(jdbc);
        series.crear(new Serie(t, TipoDocumento.GUIA_REMITENTE, "T001", 0, true));
        series.crear(new Serie(t, TipoDocumento.GUIA_TRANSPORTISTA, "V001", 41, true));
        assertThat(uow.ejecutar(() -> series.siguienteNumero(t, TipoDocumento.GUIA_REMITENTE, "T001"))).isEqualTo(1);
        assertThat(uow.ejecutar(() -> series.siguienteNumero(t, TipoDocumento.GUIA_TRANSPORTISTA, "V001"))).isEqualTo(42);
        assertThat(series.listar(t)).extracting(Serie::tipo).containsExactly(TipoDocumento.GUIA_REMITENTE, TipoDocumento.GUIA_TRANSPORTISTA);
    }

    /** V69: el tipo y sus datos van juntos también en la base; una fila que los mezcle no entra aunque la escriba otro código. */
    @Test void losCheckDeLaTablaCuidanElTipoYSusDatos() {
        UUID t = tenantDePrueba();
        GuiaRemision g = remitente(t, 1);
        uow.ejecutar(() -> repo.guardar(g));
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET motivo = NULL WHERE id = ?", g.id())).hasMessageContaining("guia_remision_datos_del_tipo");
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET registro_mtc = 'X' WHERE id = ?", g.id())).hasMessageContaining("guia_remision_datos_del_tipo");
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET serie = 'V001' WHERE id = ?", g.id())).hasMessageContaining("guia_remision_serie_del_tipo");
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET transportista_ruc = '20480072872' WHERE id = ?", g.id())).hasMessageContaining("guia_remision_transportista_publico");
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET estado = 'ENVIADO_PENDIENTE_TICKET', ticket = NULL WHERE id = ?", g.id())).hasMessageContaining("guia_remision_ticket");
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET estado = 'ANULADO' WHERE id = ?", g.id())).hasMessageContaining("guia_remision_estado");
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET peso_bruto = 0 WHERE id = ?", g.id())).hasMessageContaining("guia_remision_peso");
        GuiaRemision tr = transportista(t, 1);
        uow.ejecutar(() -> repo.guardar(tr));
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET remitente_num_doc = NULL WHERE id = ?", tr.id())).hasMessageContaining("guia_remision_datos_del_tipo");
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET modalidad = '02' WHERE id = ?", tr.id())).hasMessageContaining("guia_remision_datos_del_tipo");
    }

    /** H2: la reserva del envío es un UPDATE condicional: la primera gana, la segunda no; una ENVIANDO vieja solo se retoma con forzar; la de otra empresa, nunca. */
    @Test void elEnvioSeReservaUnaSolaVez() {
        UUID t = tenantDePrueba();
        GuiaRemision g = remitente(t, 1);
        uow.ejecutar(() -> repo.guardar(g));
        java.time.Instant ahora = java.time.Instant.parse("2026-10-20T15:00:00Z");
        assertThat(uow.ejecutar(() -> repo.reservarEnvio(t, g.id(), ahora, false, ahora.minusSeconds(600)))).isTrue();
        assertThat(repo.buscar(t, g.id()).orElseThrow().estado()).isEqualTo(EstadoGuia.ENVIANDO);
        assertThat(uow.ejecutar(() -> repo.reservarEnvio(t, g.id(), ahora, false, ahora.minusSeconds(600)))).as("ya reservada").isFalse();
        assertThat(uow.ejecutar(() -> repo.reservarEnvio(t, g.id(), ahora, true, ahora.minusSeconds(600)))).as("con forzar, todavía no es vieja").isFalse();
        assertThat(uow.ejecutar(() -> repo.reservarEnvio(t, g.id(), ahora.plusSeconds(900), true, ahora.plusSeconds(300)))).as("vieja y con forzar").isTrue();
        assertThat(uow.ejecutar(() -> repo.reservarEnvio(tenantDePrueba(), g.id(), ahora, true, ahora.plusSeconds(3600)))).isFalse();
        // Lo que vuelve de SUNAT se guarda sobre la reserva.
        GuiaRemision enviada = repo.buscar(t, g.id()).orElseThrow();
        enviada.marcarEnviada("t-1", null);
        uow.ejecutar(() -> repo.guardar(enviada));
        assertThat(repo.buscar(t, g.id()).orElseThrow().estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(uow.ejecutar(() -> repo.reservarEnvio(t, g.id(), ahora, true, ahora.plusSeconds(3600)))).as("con ticket no se reenvía").isFalse();
        assertThatThrownBy(() -> jdbc.update("UPDATE guia_remision SET estado = 'ENVIANDO', enviando_desde = NULL WHERE id = ?", g.id())).hasMessageContaining("guia_remision_enviando");
    }

    /** H3: la marca de credenciales rechazadas se guarda, se reemplaza y se levanta. */
    @Test void laMarcaDeCredencialesRechazadas() {
        UUID t = tenantDePrueba();
        JdbcRechazoDeCredencialesGreRepository rechazos = new JdbcRechazoDeCredencialesGreRepository(jdbc);
        java.time.Instant en = java.time.Instant.parse("2026-10-20T15:00:00Z");
        rechazos.marcar(t, "h1", "401 - invalid_grant", en);
        rechazos.marcar(t, "h2", "401 - otra vez", en.plusSeconds(60));
        assertThat(rechazos.buscar(t)).contains(new pe.factura.application.port.out.RechazoDeCredencialesGreRepository.Rechazo("h2", "401 - otra vez", en.plusSeconds(60)));
        rechazos.levantar(t);
        assertThat(rechazos.buscar(t)).isEmpty();
    }
}
