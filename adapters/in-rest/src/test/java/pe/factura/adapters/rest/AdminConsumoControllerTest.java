package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeCuenta;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeEmpresa;
import pe.factura.domain.DomainException;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El consumo mensual por cuenta y por empresa desde el backoffice (#192). */
@WebMvcTest(controllers = AdminConsumoController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminConsumoControllerTest {
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID EMPRESA = UUID.randomUUID();
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @Autowired MockMvc mvc;
    @MockBean ConsultarConsumoUseCase consumo;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    @Test void elConsumoDeUnaCuentaDiceElMesElTotalYElDetallePorEmpresa() throws Exception {
        when(consumo.deCuenta(CUENTA, OCTUBRE)).thenReturn(new ConsumoDeCuenta(CUENTA, OCTUBRE, 17,
                List.of(new ConsumoDeEmpresa(EMPRESA, "20100066603", "UNO SAC", OCTUBRE, 5), new ConsumoDeEmpresa(UUID.randomUUID(), "20100066611", "DOS SAC", OCTUBRE, 12))));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/consumo?mes=2026-10").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos.mes").value("2026-10"))
                .andExpect(jsonPath("$.datos.documentos").value(17))
                .andExpect(jsonPath("$.datos.empresas.length()").value(2))
                .andExpect(jsonPath("$.datos.empresas[0].empresa_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos.empresas[0].ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.empresas[0].razon_social").value("UNO SAC"))
                .andExpect(jsonPath("$.datos.empresas[0].documentos").value(5))
                .andExpect(jsonPath("$.datos.empresas[1].documentos").value(12));
    }

    @Test void elConsumoDeUnaEmpresaDiceSuRucElMesYLosDocumentos() throws Exception {
        when(consumo.deEmpresa(EMPRESA, OCTUBRE)).thenReturn(new ConsumoDeEmpresa(EMPRESA, "20100066603", "UNO SAC", OCTUBRE, 5));

        mvc.perform(get("/v1/admin/empresas/" + EMPRESA + "/consumo?mes=2026-10").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.empresa_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.razon_social").value("UNO SAC"))
                .andExpect(jsonPath("$.datos.mes").value("2026-10"))
                .andExpect(jsonPath("$.datos.documentos").value(5));
    }

    @Test void sinMesPideElMesEnCurso() throws Exception {
        when(consumo.deCuenta(CUENTA, null)).thenReturn(new ConsumoDeCuenta(CUENTA, OCTUBRE, 0, List.of()));
        when(consumo.deEmpresa(EMPRESA, null)).thenReturn(new ConsumoDeEmpresa(EMPRESA, "20100066603", "UNO SAC", OCTUBRE, 0));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/consumo").with(clave())).andExpect(status().isOk()).andExpect(jsonPath("$.datos.mes").value("2026-10"));
        mvc.perform(get("/v1/admin/empresas/" + EMPRESA + "/consumo").with(clave())).andExpect(status().isOk());

        verify(consumo).deCuenta(CUENTA, null);
        verify(consumo).deEmpresa(EMPRESA, null);
    }

    @Test void unMesVacioEsElMesEnCurso() throws Exception {
        when(consumo.deCuenta(CUENTA, null)).thenReturn(new ConsumoDeCuenta(CUENTA, OCTUBRE, 0, List.of()));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/consumo?mes=").with(clave())).andExpect(status().isOk());

        verify(consumo).deCuenta(CUENTA, null);
    }

    /** El formato es AAAA-MM y nada más: ni otro orden, ni un mes 13, ni un año de cinco dígitos, ni un día. */
    @Test void unMesMalEscritoEs400SinConsultar() throws Exception {
        for (String malo : List.of("2026-13", "2026-00", "26-10", "2026-1", "10-2026", "2026/10", "2026-10-15", "+12026-10", "octubre", "2026-10 ", "20261")) {
            mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/consumo").param("mes", malo).with(clave())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
            mvc.perform(get("/v1/admin/empresas/" + EMPRESA + "/consumo").param("mes", malo).with(clave())).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(consumo);
    }

    @Test void unaCuentaOEmpresaQueNoExistenSon404() throws Exception {
        when(consumo.deCuenta(any(), any())).thenThrow(new DomainException("NO_ENCONTRADO", "La cuenta no existe"));
        when(consumo.deEmpresa(any(), any())).thenThrow(new DomainException("NO_ENCONTRADO", "La empresa no existe"));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/consumo").with(clave())).andExpect(status().isNotFound());
        mvc.perform(get("/v1/admin/empresas/" + EMPRESA + "/consumo").with(clave())).andExpect(status().isNotFound());
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinConsultar() throws Exception {
        mvc.perform(get("/v1/admin/cuentas/no-es-un-uuid/consumo").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/empresas/no-es-un-uuid/consumo").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(consumo);
    }
}
