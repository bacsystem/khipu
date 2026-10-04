package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarPagosUseCase;
import pe.factura.application.port.in.ConsultarPagosUseCase.Pagina;
import pe.factura.application.port.in.RegistrarPagoUseCase;
import pe.factura.application.port.in.RegistrarPagoUseCase.Comando;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.MedioDePago;
import pe.factura.domain.plan.Pago;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los pagos de una cuenta desde el backoffice (#194): registrarlos a mano y ver su historial. */
@WebMvcTest(controllers = AdminPagoController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminPagoControllerTest {
    static final UUID CUENTA = UUID.randomUUID();
    static final Instant REGISTRADO = Instant.parse("2026-10-15T15:00:00Z");

    @Autowired MockMvc mvc;
    @MockBean RegistrarPagoUseCase registrar;
    @MockBean ConsultarPagosUseCase consultar;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    static Pago pago(String referencia, Instant extendio) {
        return Pago.registrar(UUID.randomUUID(), CUENTA, UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("29.00"), MedioDePago.YAPE,
                LocalDate.of(2026, 10, 14), referencia, referencia == null ? null : "Pagó por Yape", REGISTRADO, extendio);
    }

    static final String CUERPO = """
            {"periodo_desde":"2026-10-01","periodo_hasta":"2026-10-31","monto":29.00,"medio":"YAPE","fecha_de_pago":"2026-10-14","referencia":"OP-123","nota":"Pagó por Yape","extender_vencimiento":true}""";

    // --- registrar --------------------------------------------------------------------------------------------------------------------------

    @Test void registrarResponde201ConElPagoCompleto() throws Exception {
        Pago p = pago("OP-123", Instant.parse("2026-11-01T05:00:00Z"));
        when(registrar.registrar(any(), eq(CUENTA), any())).thenReturn(p);

        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/pagos").contentType(MediaType.APPLICATION_JSON).content(CUERPO).with(clave()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.id").value(p.id().toString()))
                .andExpect(jsonPath("$.datos.cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos.periodo_desde").value("2026-10-01"))
                .andExpect(jsonPath("$.datos.periodo_hasta").value("2026-10-31"))
                .andExpect(jsonPath("$.datos.monto").value(29.0))
                .andExpect(jsonPath("$.datos.medio").value("YAPE"))
                .andExpect(jsonPath("$.datos.fecha_de_pago").value("2026-10-14"))
                .andExpect(jsonPath("$.datos.referencia").value("OP-123"))
                .andExpect(jsonPath("$.datos.nota").value("Pagó por Yape"))
                .andExpect(jsonPath("$.datos.registrado_en").value("2026-10-15T15:00:00Z"))
                .andExpect(jsonPath("$.datos.extendio_hasta").value("2026-11-01T05:00:00Z"));
    }

    @Test void unPagoSinReferenciaNotaNiExtensionNoLosTraeEnLaRespuesta() throws Exception {
        when(registrar.registrar(any(), eq(CUENTA), any())).thenReturn(pago(null, null));

        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/pagos").contentType(MediaType.APPLICATION_JSON).content(CUERPO).with(clave()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.referencia").doesNotExist())
                .andExpect(jsonPath("$.datos.nota").doesNotExist())
                .andExpect(jsonPath("$.datos.extendio_hasta").doesNotExist());
    }

    @Test void pasaAlCasoDeUsoLaCuentaYTodosLosDatosDelCuerpo() throws Exception {
        when(registrar.registrar(any(), eq(CUENTA), any())).thenReturn(pago("OP-123", null));

        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/pagos").contentType(MediaType.APPLICATION_JSON).content(CUERPO).with(clave())).andExpect(status().isCreated());

        ArgumentCaptor<Comando> c = ArgumentCaptor.forClass(Comando.class);
        ArgumentCaptor<ActorAdmin> actor = ArgumentCaptor.forClass(ActorAdmin.class);
        verify(registrar).registrar(actor.capture(), eq(CUENTA), c.capture());
        assertThat(c.getValue()).isEqualTo(new Comando(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("29.00"), MedioDePago.YAPE, LocalDate.of(2026, 10, 14), "OP-123",
                "Pagó por Yape", true));
        assertThat(actor.getValue()).isNotNull();
    }

    @Test void sinPedirlaNoSeExtiendeElVencimiento() throws Exception {
        when(registrar.registrar(any(), eq(CUENTA), any())).thenReturn(pago(null, null));
        String sin = """
                {"periodo_desde":"2026-10-01","periodo_hasta":"2026-10-31","monto":29,"medio":"EFECTIVO","fecha_de_pago":"2026-10-14"}""";
        String no = sin.replace("\"fecha_de_pago\":\"2026-10-14\"", "\"fecha_de_pago\":\"2026-10-14\",\"extender_vencimiento\":false");

        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/pagos").contentType(MediaType.APPLICATION_JSON).content(sin).with(clave())).andExpect(status().isCreated());
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/pagos").contentType(MediaType.APPLICATION_JSON).content(no).with(clave())).andExpect(status().isCreated());

        ArgumentCaptor<Comando> c = ArgumentCaptor.forClass(Comando.class);
        verify(registrar, org.mockito.Mockito.times(2)).registrar(any(), eq(CUENTA), c.capture());
        assertThat(c.getAllValues()).extracting(Comando::extenderVencimiento).containsExactly(false, false);
        assertThat(c.getAllValues().get(0).referencia()).isNull();
    }

    @Test void losRechazosDelDominioSalenConSuEstado() throws Exception {
        record Caso(String codigo, int estado) {}
        for (Caso k : List.of(new Caso("NO_ENCONTRADO", 404), new Caso("PAGO_DUPLICADO", 409), new Caso("PLAN_SIN_VENCIMIENTO", 409), new Caso("EXTENSION_SIN_EFECTO", 409),
                new Caso("CAMBIO_CONCURRENTE", 409), new Caso("PERIODO_INVALIDO", 422), new Caso("MONTO_INVALIDO", 422), new Caso("MEDIO_INVALIDO", 422),
                new Caso("FECHA_DE_PAGO_INVALIDA", 422), new Caso("FECHA_DE_PAGO_FUTURA", 422), new Caso("REFERENCIA_INVALIDA", 422), new Caso("NOTA_INVALIDA", 422))) {
            org.mockito.Mockito.reset(registrar);
            when(registrar.registrar(any(), any(), any())).thenThrow(new DomainException(k.codigo(), "x"));

            mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/pagos").contentType(MediaType.APPLICATION_JSON).content(CUERPO).with(clave()))
                    .andExpect(status().is(k.estado()))
                    .andExpect(jsonPath("$.codigo").value(k.codigo()));
        }
    }

    @Test void unCuerpoQueNoEsJsonUnMedioDesconocidoOUnaFechaMalEscritaSon400SinLlamarAlCasoDeUso() throws Exception {
        for (String malo : List.of("no es json", CUERPO.replace("YAPE", "BITCOIN"), CUERPO.replace("2026-10-14", "14/10/2026"), CUERPO.replace("29.00", "\"mucho\""))) {
            mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/pagos").contentType(MediaType.APPLICATION_JSON).content(malo).with(clave())).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(registrar);
    }

    @Test void unIdQueNoEsUnUuidEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/no-es-un-uuid/pagos").contentType(MediaType.APPLICATION_JSON).content(CUERPO).with(clave())).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/cuentas/no-es-un-uuid/pagos").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(registrar, consultar);
    }

    // --- historial --------------------------------------------------------------------------------------------------------------------------

    @Test void elHistorialListaLosPagosYPonePorTotalEnLaCabecera() throws Exception {
        Pago a = pago("A", null);
        Pago b = pago("B", Instant.parse("2026-11-01T05:00:00Z"));
        when(consultar.deLaCuenta(CUENTA, 1, 20)).thenReturn(new Pagina(List.of(a, b), 37));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/pagos").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "37"))
                .andExpect(jsonPath("$.datos.length()").value(2))
                .andExpect(jsonPath("$.datos[0].id").value(a.id().toString()))
                .andExpect(jsonPath("$.datos[0].referencia").value("A"))
                .andExpect(jsonPath("$.datos[1].extendio_hasta").value("2026-11-01T05:00:00Z"));
    }

    @Test void laPaginaYElTamanoSeAcotan() throws Exception {
        when(consultar.deLaCuenta(any(), anyInt(), anyInt())).thenReturn(new Pagina(List.of(), 0));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/pagos?pagina=3&por_pagina=50").with(clave())).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/pagos?pagina=0&por_pagina=0").with(clave())).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/pagos?pagina=-2&por_pagina=5000").with(clave())).andExpect(status().isOk());

        verify(consultar).deLaCuenta(CUENTA, 3, 50);
        verify(consultar).deLaCuenta(CUENTA, 1, 1);
        verify(consultar).deLaCuenta(CUENTA, 1, 100);
    }

    @Test void elHistorialDeUnaCuentaQueNoExisteEs404() throws Exception {
        when(consultar.deLaCuenta(any(), anyInt(), anyInt())).thenThrow(new DomainException("NO_ENCONTRADO", "La cuenta no existe"));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/pagos").with(clave())).andExpect(status().isNotFound());
    }

    @Test void sinPagosElHistorialEsUnaListaVaciaConTotalCero() throws Exception {
        when(consultar.deLaCuenta(CUENTA, 1, 20)).thenReturn(new Pagina(List.of(), 0));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/pagos").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.datos.length()").value(0));
    }
}
