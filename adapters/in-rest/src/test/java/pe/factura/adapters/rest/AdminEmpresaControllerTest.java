package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;
import pe.factura.domain.tenant.Entorno;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminEmpresaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminEmpresaControllerTest {
    static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID CUENTA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final EmpresaResumen ANDINA = new EmpresaResumen(ID, "20100066603", "COMERCIAL ANDINA SAC", CUENTA, "Mi negocio", Entorno.PRODUCCION,
            EstadoCertificado.POR_VENCER, LocalDate.of(2026, 10, 20), 17, true, 2, 31, LocalDate.of(2026, 10, 2));

    @Autowired MockMvc mvc;
    @MockBean ListarEmpresasAdminUseCase listar;

    @Test void devuelveLasEmpresasConTodasLasColumnasYElTotalEnLaCabecera() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANDINA));
        when(listar.contar(Filtro.NINGUNO)).thenReturn(42L);

        mvc.perform(get("/v1/admin/empresas"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "42"))
                .andExpect(jsonPath("$.datos[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.datos[0].ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos[0].razon_social").value("COMERCIAL ANDINA SAC"))
                .andExpect(jsonPath("$.datos[0].cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos[0].cuenta_nombre").value("Mi negocio"))
                .andExpect(jsonPath("$.datos[0].entorno").value("PRODUCCION"))
                .andExpect(jsonPath("$.datos[0].certificado").value("POR_VENCER"))
                .andExpect(jsonPath("$.datos[0].certificado_vigente_hasta").value("2026-10-20"))
                .andExpect(jsonPath("$.datos[0].certificado_dias_restantes").value(17))
                .andExpect(jsonPath("$.datos[0].tiene_credenciales_sol").value(true))
                .andExpect(jsonPath("$.datos[0].series").value(2))
                .andExpect(jsonPath("$.datos[0].comprobantes_del_mes").value(31))
                .andExpect(jsonPath("$.datos[0].ultima_emision").value("2026-10-02"));
    }

    /** Como en toda la API (`non_null`): una empresa sin cuenta, sin certificado y que nunca emitió no trae esos campos. */
    @Test void loQueNoTieneValorNoAparece() throws Exception {
        EmpresaResumen nueva = new EmpresaResumen(ID, "20100066611", "INTEGRADOR SAC", null, null, Entorno.BETA, EstadoCertificado.SIN_CERTIFICADO,
                null, null, false, 0, 0, null);
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(nueva));

        mvc.perform(get("/v1/admin/empresas"))
                .andExpect(jsonPath("$.datos[0].certificado").value("SIN_CERTIFICADO"))
                .andExpect(jsonPath("$.datos[0].cuenta_id").doesNotExist())
                .andExpect(jsonPath("$.datos[0].cuenta_nombre").doesNotExist())
                .andExpect(jsonPath("$.datos[0].certificado_vigente_hasta").doesNotExist())
                .andExpect(jsonPath("$.datos[0].certificado_dias_restantes").doesNotExist())
                .andExpect(jsonPath("$.datos[0].ultima_emision").doesNotExist())
                .andExpect(jsonPath("$.datos[0].series").value(0))
                .andExpect(jsonPath("$.datos[0].comprobantes_del_mes").value(0));
    }

    @Test void nadaDeLoSecretoSeExpone() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANDINA));

        String cuerpo = mvc.perform(get("/v1/admin/empresas")).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("pkcs12", "clave", "sol_usuario", "password", "api_key");
    }

    @Test void pasaLosFiltrosYLaPaginaAlCasoDeUso() throws Exception {
        Filtro filtro = new Filtro(Entorno.PRODUCCION, EstadoCertificado.VENCIDO);
        when(listar.listar(filtro, 3, 50)).thenReturn(List.of(ANDINA));
        when(listar.contar(filtro)).thenReturn(120L);

        mvc.perform(get("/v1/admin/empresas").param("entorno", "PRODUCCION").param("certificado", "VENCIDO").param("pagina", "3").param("por_pagina", "50"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "120"));

        verify(listar).listar(filtro, 3, 50);
        verify(listar).contar(filtro);
    }

    @Test void cadaFiltroPorSiSolo() throws Exception {
        mvc.perform(get("/v1/admin/empresas").param("entorno", "BETA")).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/empresas").param("certificado", "POR_VENCER")).andExpect(status().isOk());

        verify(listar).listar(new Filtro(Entorno.BETA, null), 1, 20);
        verify(listar).listar(new Filtro(null, EstadoCertificado.POR_VENCER), 1, 20);
    }

    @Test void unValorDeFiltroQueNoExisteEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(get("/v1/admin/empresas").param("entorno", "STAGING")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/empresas").param("certificado", "CADUCADO")).andExpect(status().isBadRequest());

        verifyNoInteractions(listar);
    }

    @Test void acotaLaPaginaYElTamanoDePagina() throws Exception {
        mvc.perform(get("/v1/admin/empresas").param("pagina", "0").param("por_pagina", "500")).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/empresas").param("pagina", "-5").param("por_pagina", "0")).andExpect(status().isOk());

        verify(listar).listar(Filtro.NINGUNO, 1, 100);
        verify(listar).listar(Filtro.NINGUNO, 1, 1);
    }
}
