package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase;
import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase.Fila;
import pe.factura.application.port.in.FiltroDeConsumo;
import pe.factura.application.port.in.OrdenDeConsumo;
import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El consumo de todas las cuentas contra su plan, con alertas y exportación, desde el backoffice (#193). */
@WebMvcTest(controllers = AdminConsumoDeCuentasController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminConsumoDeCuentasControllerTest {
    static final UUID CUENTA = UUID.randomUUID();
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    static final Instant VENCE = Instant.parse("2026-10-20T05:00:00Z");

    @Autowired MockMvc mvc;
    @MockBean ConsultarConsumoDeCuentasUseCase consumo;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    static Fila alerta() {
        return new Fila(CUENTA, "Ana", "ana@x.pe", UUID.randomUUID(), "Emprende", 240, Limite.de(300), 80, true, EstadoSuscripcion.EN_GRACIA, VENCE, 5, VENCE.plusSeconds(5 * 86_400));
    }

    static Fila ilimitada() {
        return new Fila(UUID.randomUUID(), "Luis", "luis@x.pe", UUID.randomUUID(), "Pro", 9000, Limite.sinLimite(), null, false, EstadoSuscripcion.VIGENTE, null, 0, null);
    }

    @Test void listaLasCuentasConSuConsumoSuLimiteYSuEstadoDeCobro() throws Exception {
        when(consumo.listar(OCTUBRE, null, null, 1, 20)).thenReturn(List.of(alerta(), ilimitada()));
        when(consumo.contar(OCTUBRE, null)).thenReturn(57L);

        mvc.perform(get("/v1/admin/consumo?mes=2026-10").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "57"))
                .andExpect(jsonPath("$.datos.mes").value("2026-10"))
                .andExpect(jsonPath("$.datos.umbral_de_alerta").value(80))
                .andExpect(jsonPath("$.datos.cuentas.length()").value(2))
                .andExpect(jsonPath("$.datos.cuentas[0].cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos.cuentas[0].nombre").value("Ana"))
                .andExpect(jsonPath("$.datos.cuentas[0].email").value("ana@x.pe"))
                .andExpect(jsonPath("$.datos.cuentas[0].plan").value("Emprende"))
                .andExpect(jsonPath("$.datos.cuentas[0].documentos").value(240))
                .andExpect(jsonPath("$.datos.cuentas[0].limite").value(300))
                .andExpect(jsonPath("$.datos.cuentas[0].porcentaje").value(80))
                .andExpect(jsonPath("$.datos.cuentas[0].en_alerta").value(true))
                .andExpect(jsonPath("$.datos.cuentas[0].estado_del_plan").value("EN_GRACIA"))
                .andExpect(jsonPath("$.datos.cuentas[0].pagado_hasta").value("2026-10-20T05:00:00Z"))
                .andExpect(jsonPath("$.datos.cuentas[0].se_sirve_hasta").value("2026-10-25T05:00:00Z"));
    }

    @Test void unPlanSinTopeNoTraeLimiteNiPorcentajeNiFechas() throws Exception {
        when(consumo.listar(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(ilimitada()));

        mvc.perform(get("/v1/admin/consumo?mes=2026-10").with(clave()))
                .andExpect(jsonPath("$.datos.cuentas[0].en_alerta").value(false))
                .andExpect(jsonPath("$.datos.cuentas[0].estado_del_plan").value("VIGENTE"))
                .andExpect(jsonPath("$.datos.cuentas[0].limite").doesNotExist())
                .andExpect(jsonPath("$.datos.cuentas[0].porcentaje").doesNotExist())
                .andExpect(jsonPath("$.datos.cuentas[0].pagado_hasta").doesNotExist())
                .andExpect(jsonPath("$.datos.cuentas[0].se_sirve_hasta").doesNotExist());
    }

    @Test void pasaElMesElFiltroElOrdenYLaPagina() throws Exception {
        mvc.perform(get("/v1/admin/consumo?mes=2026-08&filtro=CERCA_DEL_LIMITE&orden=DOCUMENTOS&pagina=3&por_pagina=50").with(clave())).andExpect(status().isOk());

        verify(consumo).listar(YearMonth.of(2026, 8), FiltroDeConsumo.CERCA_DEL_LIMITE, OrdenDeConsumo.DOCUMENTOS, 3, 50);
        verify(consumo).contar(YearMonth.of(2026, 8), FiltroDeConsumo.CERCA_DEL_LIMITE);
    }

    /** El mes se resuelve una sola vez en el controlador: el que se dice en la respuesta es el que se midió, aunque el mes cambie a mitad de la petición. */
    @Test void sinParametrosEsElMesEnCursoQueSeResuelveUnaVezYElServicioPonePorDefectoElResto() throws Exception {
        when(consumo.mesActual()).thenReturn(OCTUBRE);

        mvc.perform(get("/v1/admin/consumo").with(clave())).andExpect(status().isOk()).andExpect(jsonPath("$.datos.mes").value("2026-10"));

        verify(consumo).listar(OCTUBRE, null, null, 1, 20);
        verify(consumo).contar(OCTUBRE, null);
    }

    @Test void laPaginaYElTamanoSeAcotan() throws Exception {
        mvc.perform(get("/v1/admin/consumo?mes=2026-10&pagina=0&por_pagina=0").with(clave())).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/consumo?mes=2026-10&pagina=-4&por_pagina=5000").with(clave())).andExpect(status().isOk());

        verify(consumo).listar(OCTUBRE, null, null, 1, 1);
        verify(consumo).listar(OCTUBRE, null, null, 1, 100);
    }

    @Test void unMesUnFiltroOUnOrdenInvalidosSon400SinConsultar() throws Exception {
        for (String malo : List.of("mes=2026-13", "mes=octubre", "mes=2026-10-01", "filtro=TODOS", "filtro=todas", "orden=NOMBRE")) {
            mvc.perform(get("/v1/admin/consumo?" + malo).with(clave())).andExpect(status().isBadRequest());
            mvc.perform(get("/v1/admin/consumo/exportacion?" + malo).with(clave())).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(consumo);
    }

    // --- la exportación ---------------------------------------------------------------------------------------------------------------------

    @Test void laExportacionEsUnCsvQueSeDescargaConElNombreDelMes() throws Exception {
        when(consumo.todas(OCTUBRE, null, null)).thenReturn(List.of(alerta()));

        var respuesta = mvc.perform(get("/v1/admin/consumo/exportacion?mes=2026-10").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"consumo-2026-10.csv\""))
                .andReturn().getResponse();

        String cuerpo = respuesta.getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).startsWith("﻿cuenta_id,cuenta,correo,plan,");
        assertThat(cuerpo).contains("Ana,ana@x.pe,Emprende,240,300,80,si,EN_GRACIA,");
    }

    @Test void laExportacionRespetaElFiltroYElOrdenYNoPagina() throws Exception {
        mvc.perform(get("/v1/admin/consumo/exportacion?mes=2026-08&filtro=PLAN_VENCIDO&orden=DOCUMENTOS").with(clave())).andExpect(status().isOk());

        verify(consumo).todas(YearMonth.of(2026, 8), FiltroDeConsumo.PLAN_VENCIDO, OrdenDeConsumo.DOCUMENTOS);
    }

    @Test void laExportacionSinMesUsaElMesEnCursoEnElNombreDelArchivo() throws Exception {
        when(consumo.mesActual()).thenReturn(OCTUBRE);

        mvc.perform(get("/v1/admin/consumo/exportacion").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"consumo-2026-10.csv\""));

        verify(consumo).todas(OCTUBRE, null, null);
    }

    @Test void sinMesNiDatosLaExportacionIgualTraeLaCabecera() throws Exception {
        when(consumo.todas(any(), any(), any())).thenReturn(List.of());
        when(consumo.mesActual()).thenReturn(OCTUBRE);

        String cuerpo = mvc.perform(get("/v1/admin/consumo/exportacion").with(clave())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(cuerpo).isEqualTo("﻿cuenta_id,cuenta,correo,plan,documentos,limite,porcentaje,en_alerta,estado_del_plan,pagado_hasta,se_sirve_hasta\r\n");
    }
}
