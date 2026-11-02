package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.GestionarClientesUseCase;
import pe.factura.application.port.in.GestionarClientesUseCase.Guardado;
import pe.factura.application.port.in.GestionarClientesUseCase.Pagina;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ClienteGuardado;
import pe.factura.domain.documento.ClienteGuardado.Datos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La API de clientes guardados (#319): su forma real (snake_case, lo opcional ausente), el total en la cabecera y el status de cada error. */
@WebMvcTest(controllers = ClientesController.class)
@Import(GlobalExceptionHandler.class)
class ClientesControllerTest {
    static final UUID TENANT = UUID.randomUUID(), ID = UUID.randomUUID();
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    static final String CREAR = "{\"tipo_doc\":\"6\",\"num_doc\":\"20601234565\",\"razon_social\":\"COMERCIAL ANDINA S.A.C.\",\"direccion\":\"Av. Lima 1\",\"email\":\"a@b.pe\"}";

    @Autowired MockMvc mvc;
    @MockitoBean GestionarClientesUseCase clientes;

    static ClienteGuardado cliente() {
        return new ClienteGuardado(ID, TENANT, "6", "20601234565", "COMERCIAL ANDINA S.A.C.", "Av. Lima 1", null, "a@b.pe", AHORA, AHORA);
    }

    @Test void crearDevuelve201ConElClienteEnSnakeCaseYSinLoNulo() throws Exception {
        when(clientes.crear(eq(TENANT), any())).thenReturn(cliente());

        mvc.perform(post("/v1/clientes").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(CREAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.tipo_doc").value("6"))
                .andExpect(jsonPath("$.datos.num_doc").value("20601234565"))
                .andExpect(jsonPath("$.datos.razon_social").value("COMERCIAL ANDINA S.A.C."))
                .andExpect(jsonPath("$.datos.direccion").value("Av. Lima 1"))
                .andExpect(jsonPath("$.datos.pais").doesNotExist())
                .andExpect(jsonPath("$.datos.creado_en").value("2026-10-19T15:00:00Z"));
        verify(clientes).crear(TENANT, new Datos("6", "20601234565", "COMERCIAL ANDINA S.A.C.", "Av. Lima 1", null, "a@b.pe"));
    }

    @Test void listarPasaElTextoYLaPaginaYDevuelveElTotalEnLaCabecera() throws Exception {
        when(clientes.listar(TENANT, "andina", 2, 10)).thenReturn(new Pagina(List.of(cliente()), 37));

        mvc.perform(get("/v1/clientes?q=andina&pagina=2&por_pagina=10").requestAttr(TenantActual.ATRIBUTO, TENANT))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "37"))
                .andExpect(jsonPath("$.datos[0].razon_social").value("COMERCIAL ANDINA S.A.C."));
    }

    @Test void guardarPorDocumentoDevuelve201SiLoCreoY200SiLoActualizo() throws Exception {
        when(clientes.guardarPorDocumento(eq(TENANT), any())).thenReturn(new Guardado(cliente(), true)).thenReturn(new Guardado(cliente(), false));
        String cuerpo = "{\"razon_social\":\"COMERCIAL ANDINA S.A.C.\"}";

        mvc.perform(put("/v1/clientes/documento/6/20601234565").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(cuerpo)).andExpect(status().isCreated());
        mvc.perform(put("/v1/clientes/documento/6/20601234565").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(cuerpo)).andExpect(status().isOk());

        verify(clientes, org.mockito.Mockito.times(2)).guardarPorDocumento(TENANT, new Datos("6", "20601234565", "COMERCIAL ANDINA S.A.C.", null, null, null));
    }

    @Test void verCambiarYEliminar() throws Exception {
        when(clientes.obtener(TENANT, ID)).thenReturn(cliente());
        when(clientes.actualizar(eq(TENANT), eq(ID), any())).thenReturn(cliente());

        mvc.perform(get("/v1/clientes/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT)).andExpect(status().isOk()).andExpect(jsonPath("$.datos.num_doc").value("20601234565"));
        mvc.perform(put("/v1/clientes/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(CREAR)).andExpect(status().isOk());
        mvc.perform(delete("/v1/clientes/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT)).andExpect(status().isNoContent());
        verify(clientes).eliminar(TENANT, ID);
    }

    @Test void losErroresDelDominioTienenSuStatus() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("DUPLICADO", "Ya tiene un cliente con el documento 6 20601234565")).when(clientes).crear(any(), any());
        mvc.perform(post("/v1/clientes").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(CREAR)).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));

        org.mockito.Mockito.doThrow(new DomainException("RECEPTOR_INVALIDO", "4207 - El DNI del comprador tiene 8 dígitos")).when(clientes).actualizar(any(), any(), any());
        mvc.perform(put("/v1/clientes/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(CREAR)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RECEPTOR_INVALIDO"));

        org.mockito.Mockito.doThrow(new DomainException("NO_ENCONTRADO", "Cliente no encontrado")).when(clientes).eliminar(any(), any());
        mvc.perform(delete("/v1/clientes/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT)).andExpect(status().isNotFound());
    }

    @Test void sinLosCamposObligatoriosEsUnaValidacion() throws Exception {
        mvc.perform(post("/v1/clientes").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content("{\"tipo_doc\":\"\",\"num_doc\":\"\",\"razon_social\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.tipoDoc").exists())
                .andExpect(jsonPath("$.errores.numDoc").exists())
                .andExpect(jsonPath("$.errores.razonSocial").exists());
        mvc.perform(put("/v1/clientes/documento/6/20601234565").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content("{}")).andExpect(status().isUnprocessableEntity());
    }

    @Test void sinEmpresaIdentificadaNoHayAcceso() throws Exception {
        mvc.perform(get("/v1/clientes")).andExpect(status().is4xxClientError()).andExpect(jsonPath("$.codigo").value("EMPRESA_REQUERIDA"));
    }
}
