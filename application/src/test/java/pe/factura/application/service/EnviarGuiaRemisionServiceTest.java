package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.EstadoGuia;
import pe.factura.domain.documento.GuiaRemision;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.factura.application.service.FakesDeGuia.*;

/** #39: el envío de la guía, la consulta del ticket y lo que queda programado en el outbox según la respuesta de SUNAT. */
class EnviarGuiaRemisionServiceTest {
    UUID tenant = UUID.randomUUID();
    Guias guias = new Guias();
    Fakes.Tenants tenants = new Fakes.Tenants();
    Fakes.Storage storage = new Fakes.Storage();
    Fakes.Outbox outbox = new Fakes.Outbox();
    Fakes.Cdrs cdrs = new Fakes.Cdrs();
    Gre gre = new Gre();
    Rechazos rechazos = new Rechazos();
    EnviarGuiaRemisionService service;

    @BeforeEach void setUp() {
        tenants.guardar(tenantGre(tenant));
        service = new EnviarGuiaRemisionService(guias, tenants, storage, gre, cdrs, outbox, Fakes.UOW, Fakes.CLOCK, rechazos);
    }

    List<String> acciones(GuiaRemision g) { return outbox.filas.stream().filter(f -> f.agregadoId().equals(g.id())).map(Fakes.Outbox.Fila::accion).toList(); }

    @Test void conElTicketEnProcesoQuedaPendienteYProgramaLaConsulta() {
        GuiaRemision g = firmada(tenant, storage, guias);
        GuiaRemision r = service.enviar(tenant, g.id());
        assertThat(gre.ultimoNombre).isEqualTo("20100066603-09-T001-1");
        assertThat(gre.ultimoXml).isEqualTo("<DespatchAdvice/>".getBytes());
        assertThat(gre.consultas).as("una consulta en el acto").isEqualTo(1);
        assertThat(r.estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(r.ticket()).isEqualTo(gre.ticket);
        assertThat(r.reintentoAutomatico()).isTrue();
        assertThat(r.consultasTicket()).isEqualTo(1);
        assertThat(acciones(g)).containsExactly(EnviarGuiaRemisionService.ACCION_TICKET);
        assertThat(outbox.filas.get(0).cuando()).isEqualTo(Fakes.CLOCK.instant().plus(Duration.ofSeconds(30)));
        assertThat(guias.buscar(tenant, g.id()).orElseThrow().estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
    }

    @Test void siSunatYaLaProcesoQuedaAceptadaConSuCdrYSinNadaProgramado() {
        GuiaRemision g = firmada(tenant, storage, guias);
        gre.respuestas.add(aceptada());
        GuiaRemision r = service.enviar(tenant, g.id());
        assertThat(r.estado()).isEqualTo(EstadoGuia.ACEPTADO);
        assertThat(r.cdrKey()).isEqualTo(tenant + "/2026/09/guias/R-20100066603-09-T001-1.zip");
        assertThat(storage.leer(r.cdrKey())).isEqualTo("cdr-zip".getBytes());
        assertThat(r.enlaceConsulta()).isEqualTo("https://e-factura.sunat.gob.pe/qr?x=1");
        assertThat(acciones(g)).isEmpty();
    }

    @Test void siSunatNoRespondeQuedaEnErrorYSeReintentaSola() {
        GuiaRemision g = firmada(tenant, storage, guias);
        gre.fallaEnvio = new SunatTransientException("0109", "Tiempo de espera agotado");
        GuiaRemision r = service.enviar(tenant, g.id());
        assertThat(r.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(r.reintentoAutomatico()).isTrue();
        assertThat(r.ultimoError()).startsWith("0109");
        assertThat(acciones(g)).containsExactly(EnviarGuiaRemisionService.ACCION_ENVIAR);
        assertThat(gre.consultas).isZero();
        // El reenvío a mano que funciona saca la tarea de reenvío y programa la del ticket.
        gre.fallaEnvio = null;
        assertThat(service.enviar(tenant, g.id()).estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(acciones(g)).containsExactly(EnviarGuiaRemisionService.ACCION_TICKET);
    }

    @Test void lasCredencialesRechazadasYElArchivoRechazadoNoSeReintentanSolos() {
        GuiaRemision g = firmada(tenant, storage, guias);
        gre.fallaEnvio = new SunatCredencialesException("401", "SUNAT no acepta las credenciales");
        GuiaRemision r = service.enviar(tenant, g.id());
        assertThat(r.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(r.reintentoAutomatico()).isFalse();
        assertThat(r.ultimoError()).contains("clave SOL").contains("no reintenta");
        assertThat(acciones(g)).isEmpty();

        rechazos.datos.clear();   // H3 frenaría el siguiente envío con las mismas credenciales: aquí se prueba el 504
        gre.fallaEnvio = new SunatRechazoException("504", "El campo nomArchivo no cumple con el formato establecido");
        r = service.enviar(tenant, g.id());
        assertThat(r.reintentoAutomatico()).isFalse();
        assertThat(r.ultimoError()).contains("504 - El campo nomArchivo").contains("no la registró");
        assertThat(acciones(g)).isEmpty();

        gre.fallaEnvio = new DomainException("CREDENCIALES_API_SUNAT_NO_CARGADAS", "sin credenciales");
        r = service.enviar(tenant, g.id());
        assertThat(r.reintentoAutomatico()).isFalse();
        assertThat(r.ultimoError()).startsWith("CREDENCIALES_API_SUNAT_NO_CARGADAS");
        assertThat(r.intentos()).isEqualTo(3);
    }

    @Test void unErrorDeProgramacionNoSeEscondeComoErrorDeEnvio() {
        GuiaRemision g = firmada(tenant, storage, guias);
        gre.fallaEnvio = new DomainException("TICKET_INVALIDO", "otro");
        assertThatThrownBy(() -> service.enviar(tenant, g.id())).hasFieldOrPropertyWithValue("codigo", "TICKET_INVALIDO");
        assertThat(guias.buscar(tenant, g.id()).orElseThrow().estado()).as("no queda reservada: no salió nada").isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(guias.buscar(tenant, g.id()).orElseThrow().reintentoAutomatico()).isFalse();
    }

    @Test void soloSeEnviaUnaFirmadaOEnErrorYSoloSeConsultaUnaPendiente() {
        GuiaRemision g = firmada(tenant, storage, guias);
        assertThatThrownBy(() -> service.consultarTicket(tenant, g.id())).hasFieldOrPropertyWithValue("codigo", "TICKET_NO_PENDIENTE");
        service.enviar(tenant, g.id());
        assertThatThrownBy(() -> service.enviar(tenant, g.id())).hasFieldOrPropertyWithValue("codigo", "ESTADO_NO_ENVIABLE");
        assertThatThrownBy(() -> service.enviar(UUID.randomUUID(), g.id())).hasFieldOrPropertyWithValue("codigo", "NO_ENCONTRADO");
    }

    GuiaRemision pendiente() {
        GuiaRemision g = firmada(tenant, storage, guias);
        service.enviar(tenant, g.id());
        return guias.buscar(tenant, g.id()).orElseThrow();
    }

    @Test void unTicketConErrorRechazaOQuedaEnErrorSinReintento() {
        GuiaRemision a = pendiente();
        gre.respuestas.add(conError("2555", "Destinatario no debe ser igual al remitente"));
        GuiaRemision r = service.consultarTicket(tenant, a.id());
        assertThat(r.estado()).isEqualTo(EstadoGuia.RECHAZADO);
        assertThat(r.cdr()).isEqualTo(new Cdr("2555", "Destinatario no debe ser igual al remitente", List.of()));
        assertThat(acciones(a)).isEmpty();

        cdrs.cdr = new Cdr("3343", "La Fecha de inicio del traslado debe ser mayor o igual a la Fecha de emision", List.of());
        guias.datos.clear(); outbox.filas.clear();
        GuiaRemision b = pendiente();
        gre.respuestas.add(new pe.factura.application.port.out.SunatGreGateway.EstadoDelTicket("99", "cdr".getBytes(), "3343", "x", null));
        assertThat(service.consultarTicket(tenant, b.id()).estado()).as("con CDR manda el CDR").isEqualTo(EstadoGuia.RECHAZADO);

        guias.datos.clear(); outbox.filas.clear();
        GuiaRemision c = pendiente();
        gre.respuestas.add(conError("1033", "El comprobante fue registrado previamente con otros datos"));
        GuiaRemision rc = service.consultarTicket(tenant, c.id());
        assertThat(rc.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(rc.reintentoAutomatico()).isFalse();
        assertThat(acciones(c)).isEmpty();
    }

    @Test void conLaAceptacionSinCdrQuedaAceptada() {
        GuiaRemision g = pendiente();
        gre.respuestas.add(new pe.factura.application.port.out.SunatGreGateway.EstadoDelTicket("0", null, null, null, null));
        GuiaRemision r = service.consultarTicket(tenant, g.id());
        assertThat(r.estado()).isEqualTo(EstadoGuia.ACEPTADO);
        assertThat(r.cdrKey()).isNull();
    }

    @Test void unaCaidaAlConsultarSigueConsultandoYUnTicketDesconocidoNo() {
        GuiaRemision g = pendiente();
        gre.fallaTicket = new SunatTransientException("0000", "Error de red");
        GuiaRemision r = service.consultarTicket(tenant, g.id());
        assertThat(r.estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(r.reintentoAutomatico()).isTrue();
        assertThat(r.ultimoError()).startsWith("0000");
        assertThat(acciones(g)).containsExactly(EnviarGuiaRemisionService.ACCION_TICKET);

        gre.fallaTicket = new SunatRechazoException("0509", "Número de ticket no existe");
        r = service.consultarTicket(tenant, g.id());
        assertThat(r.reintentoAutomatico()).isFalse();
        assertThat(r.ultimoError()).contains("no reconoce el ticket").contains("0509");
        assertThat(acciones(g)).isEmpty();

        gre.fallaTicket = new SunatCredencialesException("401", "credenciales");
        assertThat(service.consultarTicket(tenant, g.id()).reintentoAutomatico()).isFalse();
    }

    @Test void pasadoElTopeDejaDeConsultarSolaYAMANoReanuda() {
        GuiaRemision g = pendiente();
        GuiaRemision cerca = guias.buscar(tenant, g.id()).orElseThrow();
        for (int i = cerca.consultasTicket(); i < EnviarGuiaRemisionService.TOPE_DE_CONSULTAS - 1; i++) cerca.ticketEnProceso();
        guias.guardar(cerca);
        GuiaRemision r = service.consultarTicket(tenant, g.id());   // la consulta del tope, desde el outbox o a mano con el reintento todavía activo
        assertThat(r.consultasTicket()).isEqualTo(EnviarGuiaRemisionService.TOPE_DE_CONSULTAS);
        assertThat(r.reintentoAutomatico()).isFalse();
        assertThat(r.ultimoError()).contains("dejó de consultarlo solo");
        assertThat(acciones(g)).isEmpty();
        // Pedida a mano, si SUNAT sigue en 98, vuelve a consultar sola desde cero.
        GuiaRemision reanudada = service.consultarTicket(tenant, g.id());
        assertThat(reanudada.reintentoAutomatico()).isTrue();
        assertThat(reanudada.consultasTicket()).isZero();
        assertThat(acciones(g)).containsExactly(EnviarGuiaRemisionService.ACCION_TICKET);
    }

    @Test void unaRespuestaTardiaNoPisaUnaGuiaYaResuelta() {
        GuiaRemision g = pendiente();
        gre.alConsultar = () -> {
            GuiaRemision otra = guias.buscar(tenant, g.id()).orElseThrow();
            otra.aplicarCdr(new Cdr("0", "aceptada", List.of()), "r.zip", null);
            guias.guardar(otra);
        };
        GuiaRemision r = service.consultarTicket(tenant, g.id());   // esta consulta todavía ve 98
        assertThat(r.estado()).isEqualTo(EstadoGuia.ACEPTADO);
        assertThat(guias.buscar(tenant, g.id()).orElseThrow().estado()).isEqualTo(EstadoGuia.ACEPTADO);
    }

    @Test void elIntervaloEntreConsultasCreceHastaCincoMinutos() {
        assertThat(EnviarGuiaRemisionService.intervaloDeConsulta(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(EnviarGuiaRemisionService.intervaloDeConsulta(4)).isEqualTo(Duration.ofMinutes(2));
        assertThat(EnviarGuiaRemisionService.intervaloDeConsulta(50)).isEqualTo(Duration.ofMinutes(5));
    }

    /** H2: dos envíos a la vez de la misma guía en error (el outbox y uno a mano) llaman a SUNAT una sola vez: el segundo encuentra la reserva. */
    @Test void dosEnviosALaVezLlamanASunatUnaSolaVez() throws Exception {
        GuiaRemision g = firmada(tenant, storage, guias);
        gre.fallaEnvio = new SunatTransientException("0109", "tiempo agotado");
        service.enviar(tenant, g.id());
        gre.fallaEnvio = null;
        int antes = gre.envios;
        java.util.concurrent.CountDownLatch dentro = new java.util.concurrent.CountDownLatch(1), suelta = new java.util.concurrent.CountDownLatch(1);
        gre.alEnviar = () -> { dentro.countDown(); try { suelta.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } };
        java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var primero = hilos.submit(() -> service.enviar(tenant, g.id()));
            dentro.await();   // el primero ya está hablando con SUNAT
            var segundo = hilos.submit(() -> service.enviar(tenant, g.id()));
            Throwable t = org.assertj.core.api.Assertions.catchThrowable(segundo::get);
            assertThat(t).hasCauseInstanceOf(DomainException.class);
            assertThat(((DomainException) t.getCause()).codigo()).isIn("ESTADO_NO_ENVIABLE", "ENVIO_EN_CURSO");
            suelta.countDown();
            assertThat(primero.get().estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        } finally { suelta.countDown(); hilos.shutdownNow(); }
        assertThat(gre.envios - antes).isEqualTo(1);
    }

    /** H2: una guía que quedó ENVIANDO (el proceso murió a mitad) no se reenvía sola ni sin forzar; con forzar, solo pasada la ventana. */
    @Test void unaGuiaQueQuedoEnviandoSoloSeRetomaAManoYASabiendas() {
        GuiaRemision g = firmada(tenant, storage, guias);
        assertThat(guias.reservarEnvio(tenant, g.id(), Fakes.CLOCK.instant().minusSeconds(60), false, Fakes.CLOCK.instant())).isTrue();   // se cayó tras reservar
        assertThatThrownBy(() -> service.enviar(tenant, g.id())).hasFieldOrPropertyWithValue("codigo", "ENVIO_EN_CURSO").hasMessageContaining("revise en SOL");
        assertThatThrownBy(() -> service.enviar(tenant, g.id(), true)).as("hace un minuto: todavía no").hasFieldOrPropertyWithValue("codigo", "ENVIO_EN_CURSO");
        assertThat(gre.envios).isZero();
        guias.enviandoDesde.put(g.id(), Fakes.CLOCK.instant().minus(Duration.ofMinutes(11)));
        assertThat(service.enviar(tenant, g.id(), true).estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(gre.envios).isEqualTo(1);
    }

    /** H1: apagado en el servidor, el reenvío a mano responde 422 sin llamar a SUNAT ni tocar la guía. */
    @Test void conElEnvioApagadoNoSeReenvia() {
        GuiaRemision g = firmada(tenant, storage, guias);
        gre.disponible = false;
        assertThatThrownBy(() -> service.enviar(tenant, g.id())).hasFieldOrPropertyWithValue("codigo", "GRE_NO_DISPONIBLE");
        assertThat(gre.envios).isZero();
        assertThat(guias.buscar(tenant, g.id()).orElseThrow().estado()).isEqualTo(EstadoGuia.FIRMADO);
    }

    /** H3: tras un 401 del token con una guía, la siguiente no pide token hasta que cambien las credenciales; una respuesta de SUNAT levanta la marca. */
    @Test void unRechazoDeCredencialesFrenaALaEmpresa() {
        GuiaRemision a = firmada(tenant, storage, guias);
        gre.fallaEnvio = new SunatCredencialesException("401", "SUNAT no acepta las credenciales API SUNAT (HTTP 401)");
        service.enviar(tenant, a.id());
        assertThat(rechazos.datos).containsKey(tenant);
        gre.fallaEnvio = null;
        assertThatThrownBy(() -> service.enviar(tenant, a.id())).hasFieldOrPropertyWithValue("codigo", "CREDENCIALES_GRE_RECHAZADAS");
        assertThat(gre.envios).isEqualTo(1);
        assertThat(guias.buscar(tenant, a.id()).orElseThrow().estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        tenants.guardar(tenantGre(tenant).conCredencialesApiSunat(new pe.factura.domain.tenant.CredencialesApiSunat("otro-id", "otro-secreto")));
        assertThat(service.enviar(tenant, a.id()).estado()).isEqualTo(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        assertThat(rechazos.datos).as("SUNAT las aceptó").doesNotContainKey(tenant);
    }

    /** H4: agotar los intentos deja la guía en error sin reintento automático, con el motivo, y saca la tarea del outbox. */
    @Test void agotarLosIntentosApagaElReintento() {
        GuiaRemision g = firmada(tenant, storage, guias);
        gre.fallaEnvio = new SunatTransientException("0109", "tiempo agotado");
        service.enviar(tenant, g.id());
        GuiaRemision r = service.agotarReintentos(tenant, g.id(), 20);
        assertThat(r.reintentoAutomatico()).isFalse();
        assertThat(r.ultimoError()).contains("agotó 20 intentos").contains("0109");
        assertThat(acciones(g)).isEmpty();
    }

    /** H7: un 99 con CDR cuyo código dice que el número ya está en SUNAT no rechaza la guía ni invita a reenviarla. */
    @Test void unCdrDeYaPresentadoNoRechaza() {
        GuiaRemision g = pendiente();
        cdrs.cdr = new Cdr("2223", "El archivo ya fue presentado anteriormente", List.of());
        gre.respuestas.add(new pe.factura.application.port.out.SunatGreGateway.EstadoDelTicket("99", "cdr".getBytes(), null, null, null));
        GuiaRemision r = service.consultarTicket(tenant, g.id());
        assertThat(r.estado()).isEqualTo(EstadoGuia.ERROR_ENVIO);
        assertThat(r.reintentoAutomatico()).isFalse();
        assertThat(r.ultimoError()).contains("ya está registrada en SUNAT");
    }
}
