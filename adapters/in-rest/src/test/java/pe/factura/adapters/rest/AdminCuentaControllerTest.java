package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ListarCuentasAdminUseCase;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCuentaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminCuentaControllerTest {
    static final UUID ID = UUID.randomUUID();
    static final CuentaResumen ANA = new CuentaResumen(ID, "Mi negocio", "ana@negocio.pe", "987654321",
            Instant.parse("2026-09-01T10:00:00Z"), 2, Instant.parse("2026-10-01T09:00:00Z"));

    @Autowired MockMvc mvc;
    @MockBean ListarCuentasAdminUseCase listar;

    @Test void devuelveLasCuentasConElTotalEnLaCabecera() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANA));
        when(listar.contar(Filtro.NINGUNO)).thenReturn(42L);

        mvc.perform(get("/v1/admin/cuentas"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "42"))
                .andExpect(jsonPath("$.datos[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.datos[0].nombre").value("Mi negocio"))
                .andExpect(jsonPath("$.datos[0].email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos[0].telefono").value("987654321"))
                .andExpect(jsonPath("$.datos[0].creada_en").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.datos[0].empresas").value(2))
                .andExpect(jsonPath("$.datos[0].ultimo_acceso").value("2026-10-01T09:00:00Z"));
    }

    @Test void noInventaColumnasQueTodaviaNoExisten() throws Exception {
        // Estado y plan llegan con #182 y #189: hasta entonces no se exponen con un valor fijo.
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANA));

        mvc.perform(get("/v1/admin/cuentas"))
                .andExpect(jsonPath("$.datos[0].estado").doesNotExist())
                .andExpect(jsonPath("$.datos[0].plan").doesNotExist());
    }

    /** Como en toda la API (`default-property-inclusion: non_null`), un campo sin valor no aparece: el portal lo trata como opcional. */
    @Test void unaCuentaQueNuncaInicioSesionNoTraElUltimoAcceso() throws Exception {
        CuentaResumen nunca = new CuentaResumen(ID, "Nueva", "nueva@x.pe", null, Instant.parse("2026-09-01T10:00:00Z"), 0, null);
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(nunca));

        mvc.perform(get("/v1/admin/cuentas"))
                .andExpect(jsonPath("$.datos[0].ultimo_acceso").doesNotExist())
                .andExpect(jsonPath("$.datos[0].telefono").doesNotExist())
                .andExpect(jsonPath("$.datos[0].empresas").value(0));
    }

    @Test void pasaLaBusquedaYLaPaginaAlCasoDeUso() throws Exception {
        when(listar.listar(new Filtro("ana"), 3, 50)).thenReturn(List.of(ANA));
        when(listar.contar(new Filtro("ana"))).thenReturn(120L);

        mvc.perform(get("/v1/admin/cuentas").param("q", "ana").param("pagina", "3").param("por_pagina", "50"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "120"));

        verify(listar).listar(new Filtro("ana"), 3, 50);
        verify(listar).contar(new Filtro("ana"));
    }

    @Test void acotaLaPaginaYElTamanoDePagina() throws Exception {
        mvc.perform(get("/v1/admin/cuentas").param("pagina", "0").param("por_pagina", "500")).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/cuentas").param("pagina", "-5").param("por_pagina", "0")).andExpect(status().isOk());

        verify(listar).listar(Filtro.NINGUNO, 1, 100);
        verify(listar).listar(Filtro.NINGUNO, 1, 1);
    }
}
