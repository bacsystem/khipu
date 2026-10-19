package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.GestionarProductosUseCase;
import pe.factura.application.port.in.GestionarProductosUseCase.Pagina;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ProductoGuardado;
import pe.factura.domain.documento.ProductoGuardado.Datos;

import java.math.BigDecimal;
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

/** La API de productos guardados (#320): su forma real (snake_case, lo opcional ausente, el precio como número), el total en la cabecera y el status de cada error. */
@WebMvcTest(controllers = ProductosController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class ProductosControllerTest {
    static final UUID TENANT = UUID.randomUUID(), ID = UUID.randomUUID();
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    static final String GUARDAR = "{\"codigo\":\"SKU-1\",\"descripcion\":\"Servicio de consultoría\",\"unidad\":\"ZZ\",\"tipo_afectacion_igv\":\"10\",\"precio_unitario\":118.00}";

    @Autowired MockMvc mvc;
    @MockBean GestionarProductosUseCase productos;

    static ProductoGuardado producto() {
        return new ProductoGuardado(ID, TENANT, "SKU-1", "Servicio de consultoría", "ZZ", "10", new BigDecimal("118.00"), null, AHORA, AHORA);
    }

    @Test void crearDevuelve201ConElProductoEnSnakeCaseYSinLoNulo() throws Exception {
        when(productos.crear(eq(TENANT), any())).thenReturn(producto());

        mvc.perform(post("/v1/productos").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(GUARDAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.codigo").value("SKU-1"))
                .andExpect(jsonPath("$.datos.descripcion").value("Servicio de consultoría"))
                .andExpect(jsonPath("$.datos.unidad").value("ZZ"))
                .andExpect(jsonPath("$.datos.tipo_afectacion_igv").value("10"))
                .andExpect(jsonPath("$.datos.precio_unitario").value(118.0))
                .andExpect(jsonPath("$.datos.codigo_sunat").doesNotExist())
                .andExpect(jsonPath("$.datos.creado_en").value("2026-10-19T15:00:00Z"));
        verify(productos).crear(TENANT, new Datos("SKU-1", "Servicio de consultoría", "ZZ", "10", new BigDecimal("118.00"), null));
    }

    @Test void listarPasaElTextoYLaPaginaYDevuelveElTotalEnLaCabecera() throws Exception {
        when(productos.listar(TENANT, "consult", 2, 10)).thenReturn(new Pagina(List.of(producto()), 37));

        mvc.perform(get("/v1/productos?q=consult&pagina=2&por_pagina=10").requestAttr(TenantActual.ATRIBUTO, TENANT))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "37"))
                .andExpect(jsonPath("$.datos[0].descripcion").value("Servicio de consultoría"));
    }

    @Test void verCambiarYEliminar() throws Exception {
        when(productos.obtener(TENANT, ID)).thenReturn(producto());
        when(productos.actualizar(eq(TENANT), eq(ID), any())).thenReturn(producto());

        mvc.perform(get("/v1/productos/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT)).andExpect(status().isOk()).andExpect(jsonPath("$.datos.codigo").value("SKU-1"));
        mvc.perform(put("/v1/productos/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(GUARDAR)).andExpect(status().isOk());
        mvc.perform(delete("/v1/productos/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT)).andExpect(status().isNoContent());
        verify(productos).eliminar(TENANT, ID);
    }

    @Test void losErroresDelDominioTienenSuStatus() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("DUPLICADO", "Ya tiene un producto con el código SKU-1")).when(productos).crear(any(), any());
        mvc.perform(post("/v1/productos").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(GUARDAR)).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));

        org.mockito.Mockito.doThrow(new DomainException("ITEM_INVALIDO", "2936 - La unidad de medida XYZ no existe en el catálogo 03")).when(productos).actualizar(any(), any(), any());
        mvc.perform(put("/v1/productos/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content(GUARDAR)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ITEM_INVALIDO"));

        org.mockito.Mockito.doThrow(new DomainException("NO_ENCONTRADO", "Producto no encontrado")).when(productos).eliminar(any(), any());
        mvc.perform(delete("/v1/productos/{id}", ID).requestAttr(TenantActual.ATRIBUTO, TENANT)).andExpect(status().isNotFound());
    }

    @Test void sinLosCamposObligatoriosEsUnaValidacion() throws Exception {
        mvc.perform(post("/v1/productos").requestAttr(TenantActual.ATRIBUTO, TENANT).contentType("application/json").content("{\"descripcion\":\"\",\"unidad\":\"\",\"tipo_afectacion_igv\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.descripcion").exists())
                .andExpect(jsonPath("$.errores.unidad").exists())
                .andExpect(jsonPath("$.errores.tipoAfectacionIgv").exists())
                .andExpect(jsonPath("$.errores.precioUnitario").exists());
    }

    @Test void sinEmpresaIdentificadaNoHayAcceso() throws Exception {
        mvc.perform(get("/v1/productos")).andExpect(status().is4xxClientError()).andExpect(jsonPath("$.codigo").value("EMPRESA_REQUERIDA"));
    }
}
