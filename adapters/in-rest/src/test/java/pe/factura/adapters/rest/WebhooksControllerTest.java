package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.factura.application.port.in.GestionarWebhooksUseCase;
import pe.factura.application.port.in.GestionarWebhooksUseCase.Creado;
import pe.factura.application.port.in.GestionarWebhooksUseCase.PaginaEntregas;
import pe.factura.domain.DomainException;
import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;
import pe.factura.domain.webhook.EntregaWebhook.Estado;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La API de webhooks (#19). El portal escribe sus tipos a mano a partir de este JSON: se fija su forma real (snake_case, el secreto solo al crear o rotar), el status de cada
 * error del dominio y, sobre todo, que lo que cambia a dónde van los eventos exige la sesión del portal y no una API key.
 */
@WebMvcTest(controllers = WebhooksController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class WebhooksControllerTest {
    static final UUID TENANT = UUID.randomUUID();
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID ID = UUID.randomUUID();
    static final Instant AHORA = Instant.parse("2026-10-18T15:00:00Z");
    static final String CREAR = "{\"url\":\"https://mi-sistema.pe/khipu\",\"descripcion\":\"ERP\",\"eventos\":[\"comprobante.aceptado\"]}";

    @Autowired MockMvc mvc;
    @MockBean GestionarWebhooksUseCase webhooks;

    /** Una petición con la sesión del portal (JWT): trae empresa y cuenta. */
    static RequestPostProcessor sesion() { return r -> { r.setAttribute(TenantActual.ATRIBUTO, TENANT); r.setAttribute(CuentaActual.ATRIBUTO, CUENTA); return r; }; }

    /** Una petición con API key: trae empresa y no cuenta. */
    static RequestPostProcessor apiKey() { return r -> { r.setAttribute(TenantActual.ATRIBUTO, TENANT); return r; }; }

    static EndpointWebhook endpoint(boolean activo, String pausa) {
        return new EndpointWebhook(ID, TENANT, "https://mi-sistema.pe/khipu", "ERP", List.of("comprobante.aceptado"), activo, pausa, activo ? 0 : 10, AHORA);
    }

    @Test void crearDevuelveElEndpointYElSecretoUnaSolaVez() throws Exception {
        when(webhooks.crear(eq(TENANT), eq("https://mi-sistema.pe/khipu"), eq("ERP"), eq(List.of("comprobante.aceptado")))).thenReturn(new Creado(endpoint(true, null), "whsec_abc"));

        mvc.perform(post("/v1/webhooks").with(sesion()).contentType("application/json").content(CREAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.secreto").value("whsec_abc"))
                .andExpect(jsonPath("$.datos.webhook.id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.webhook.url").value("https://mi-sistema.pe/khipu"))
                .andExpect(jsonPath("$.datos.webhook.eventos[0]").value("comprobante.aceptado"))
                .andExpect(jsonPath("$.datos.webhook.activo").value(true))
                .andExpect(jsonPath("$.datos.webhook.fallos_consecutivos").value(0))
                .andExpect(jsonPath("$.datos.webhook.creado_en").value("2026-10-18T15:00:00Z"));
    }

    @Test void listarNuncaDevuelveElSecretoYFuncionaConApiKey() throws Exception {
        when(webhooks.listar(TENANT)).thenReturn(List.of(endpoint(false, "Se apagó solo tras 10 entregas fallidas seguidas")));

        mvc.perform(get("/v1/webhooks").with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].activo").value(false))
                .andExpect(jsonPath("$.datos[0].pausado_motivo").value("Se apagó solo tras 10 entregas fallidas seguidas"))
                .andExpect(jsonPath("$.datos[0].fallos_consecutivos").value(10))
                .andExpect(jsonPath("$.datos[0].secreto").doesNotExist());
    }

    @Test void loQueCambiaElDestinoDeLosEventosExigeLaSesionDelPortalYNoUnaApiKey() throws Exception {
        mvc.perform(post("/v1/webhooks").with(apiKey()).contentType("application/json").content(CREAR)).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("REQUIERE_SESION"));
        mvc.perform(patch("/v1/webhooks/{id}", ID).with(apiKey()).contentType("application/json").content("{\"url\":\"https://otro.pe/h\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/webhooks/{id}", ID).with(apiKey())).andExpect(status().isForbidden());
        mvc.perform(post("/v1/webhooks/{id}/secreto", ID).with(apiKey())).andExpect(status().isForbidden());
        mvc.perform(post("/v1/webhooks/{id}/prueba", ID).with(apiKey())).andExpect(status().isForbidden());
        mvc.perform(post("/v1/webhooks/entregas/{id}/reenviar", ID).with(apiKey())).andExpect(status().isForbidden());
        verifyNoInteractions(webhooks);
    }

    @Test void consultarElLogYLosEventosFuncionaConApiKey() throws Exception {
        EntregaWebhook e = new EntregaWebhook(UUID.randomUUID(), ID, TENANT, "comprobante.aceptado", UUID.randomUUID(), "{}", Estado.PENDIENTE, 2, AHORA.plusSeconds(300), 503, "El servidor respondió HTTP 503", AHORA, null, AHORA);
        EntregaWebhook ok = new EntregaWebhook(UUID.randomUUID(), ID, TENANT, "webhook.prueba", null, "{}", Estado.EXITOSA, 1, AHORA, 200, null, AHORA, AHORA, AHORA);
        when(webhooks.entregas(TENANT, ID, null, 1, 20)).thenReturn(new PaginaEntregas(List.of(e, ok), 42));

        mvc.perform(get("/v1/webhooks/{id}/entregas", ID).with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "42"))
                .andExpect(jsonPath("$.datos[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.datos[0].intentos").value(2))
                .andExpect(jsonPath("$.datos[0].siguiente_intento").value("2026-10-18T15:05:00Z"))
                .andExpect(jsonPath("$.datos[0].ultimo_http").value(503))
                .andExpect(jsonPath("$.datos[0].comprobante_id").exists())
                .andExpect(jsonPath("$.datos[1].estado").value("EXITOSA"))
                .andExpect(jsonPath("$.datos[1].siguiente_intento").doesNotExist())
                .andExpect(jsonPath("$.datos[1].comprobante_id").doesNotExist())
                .andExpect(jsonPath("$.datos[1].payload").doesNotExist());
        mvc.perform(get("/v1/webhooks/eventos").with(apiKey())).andExpect(status().isOk()).andExpect(jsonPath("$.datos.length()").value(7)).andExpect(jsonPath("$.datos[0]").value("comprobante.aceptado"));
    }

    @Test void elFiltroDeEstadoYLaPaginaLlegaAlCasoDeUso() throws Exception {
        when(webhooks.entregas(TENANT, ID, Estado.FALLIDA, 3, 50)).thenReturn(new PaginaEntregas(List.of(), 0));

        mvc.perform(get("/v1/webhooks/{id}/entregas?estado=FALLIDA&pagina=3&por_pagina=50", ID).with(apiKey())).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "0"));
        mvc.perform(get("/v1/webhooks/{id}/entregas?estado=INVENTADA", ID).with(apiKey())).andExpect(status().isBadRequest());
    }

    @Test void actualizarYReactivar() throws Exception {
        when(webhooks.actualizar(TENANT, ID, null, null, null, true)).thenReturn(endpoint(true, null));

        mvc.perform(patch("/v1/webhooks/{id}", ID).with(sesion()).contentType("application/json").content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.activo").value(true));
    }

    @Test void rotarElSecretoLoMuestraUnaVezYProbarEncolaYDevuelveLaEntrega() throws Exception {
        UUID entrega = UUID.randomUUID();
        when(webhooks.rotarSecreto(TENANT, ID)).thenReturn("whsec_nuevo");
        when(webhooks.probar(TENANT, ID)).thenReturn(entrega);

        mvc.perform(post("/v1/webhooks/{id}/secreto", ID).with(sesion())).andExpect(status().isOk()).andExpect(jsonPath("$.datos.secreto").value("whsec_nuevo"));
        mvc.perform(post("/v1/webhooks/{id}/prueba", ID).with(sesion())).andExpect(status().isAccepted()).andExpect(jsonPath("$.datos.entrega_id").value(entrega.toString()));
    }

    @Test void eliminarYReenviar() throws Exception {
        UUID entrega = UUID.randomUUID();

        mvc.perform(delete("/v1/webhooks/{id}", ID).with(sesion())).andExpect(status().isNoContent());
        mvc.perform(post("/v1/webhooks/entregas/{id}/reenviar", entrega).with(sesion())).andExpect(status().isAccepted());

        verify(webhooks).eliminar(TENANT, ID);
        verify(webhooks).reenviar(TENANT, entrega);
    }

    @Test void losErroresDelDominioTienenSuStatus() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("WEBHOOK_LIMITE", "Una empresa puede tener hasta 5 webhooks")).when(webhooks).crear(any(), any(), any(), any());
        mvc.perform(post("/v1/webhooks").with(sesion()).contentType("application/json").content(CREAR)).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("WEBHOOK_LIMITE"));

        org.mockito.Mockito.doThrow(new DomainException("WEBHOOK_INVALIDO", "La URL del webhook debe empezar con https://")).when(webhooks).crear(any(), any(), any(), any());
        mvc.perform(post("/v1/webhooks").with(sesion()).contentType("application/json").content(CREAR)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("WEBHOOK_INVALIDO"));

        org.mockito.Mockito.doThrow(new DomainException("WEBHOOK_PAUSADO", "El webhook está apagado")).when(webhooks).probar(TENANT, ID);
        mvc.perform(post("/v1/webhooks/{id}/prueba", ID).with(sesion())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("WEBHOOK_PAUSADO"));

        org.mockito.Mockito.doThrow(new DomainException("ENTREGA_NO_REENVIABLE", "pendiente")).when(webhooks).reenviar(any(), any());
        mvc.perform(post("/v1/webhooks/entregas/{id}/reenviar", ID).with(sesion())).andExpect(status().isConflict());

        org.mockito.Mockito.doThrow(new DomainException("NO_ENCONTRADO", "Webhook no encontrado")).when(webhooks).eliminar(any(), any());
        mvc.perform(delete("/v1/webhooks/{id}", ID).with(sesion())).andExpect(status().isNotFound());
    }

    @Test void sinUrlOSinEventosEsUnaValidacion() throws Exception {
        mvc.perform(post("/v1/webhooks").with(sesion()).contentType("application/json").content("{\"url\":\"\",\"eventos\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.url").exists())
                .andExpect(jsonPath("$.errores.eventos").exists());
        verifyNoInteractions(webhooks);
    }
}
