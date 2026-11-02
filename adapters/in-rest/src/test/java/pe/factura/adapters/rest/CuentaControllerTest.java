package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeCuenta;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase.MiCuenta;
import pe.factura.domain.plan.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C1/C7: lo que el cliente ve de su cuenta en el portal. */
@WebMvcTest(controllers = CuentaController.class)
@Import(GlobalExceptionHandler.class)
class CuentaControllerTest {
    static final UUID CUENTA = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean ConsultarMiCuentaUseCase miCuenta;

    static MiCuenta con(Limite documentos, Instant venceEn, EstadoSuscripcion estado) {
        Plan plan = new Plan(UUID.randomUUID(), "Emprende", new BigDecimal("29.00"), new Limites(documentos, 1, Limite.de(1), Limite.de(2), 5), EstadoPlan.ACTIVO, false);
        Suscripcion s = new Suscripcion(UUID.randomUUID(), CUENTA, plan.id(), Instant.parse("2026-10-08T17:00:00Z"), venceEn, 5, null);
        return new MiCuenta(CUENTA, "Ferretería Torres", new PlanDeCuenta(CUENTA, plan, s, estado, s.hastaCuandoCubre(), null),
                new ConsumoDeCuenta(CUENTA, YearMonth.of(2026, 10), 12, List.of()), new UsoDeRecursos(1, 2, 1));
    }

    @Test void devuelveElNombreElPlanYElConsumoContraElTope() throws Exception {
        when(miCuenta.deLaCuenta(CUENTA)).thenReturn(con(Limite.de(300), Instant.parse("2027-01-09T05:00:00Z"), EstadoSuscripcion.VIGENTE));

        mvc.perform(get("/v1/cuenta").requestAttr(CuentaActual.ATRIBUTO, CUENTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombre").value("Ferretería Torres"))
                .andExpect(jsonPath("$.datos.plan.plan.nombre").value("Emprende"))
                .andExpect(jsonPath("$.datos.plan.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.datos.plan.vence_en").value("2027-01-09T05:00:00Z"))
                .andExpect(jsonPath("$.datos.consumo.mes").value("2026-10"))
                .andExpect(jsonPath("$.datos.consumo.documentos").value(12))
                .andExpect(jsonPath("$.datos.consumo.maximo").value(300));
    }

    /** #498: lo que ocupa de los límites que no son documentos, en snake_case como el resto. */
    @Test void devuelveLoQueOcupaDeApiKeysUsuariosYRucs() throws Exception {
        when(miCuenta.deLaCuenta(CUENTA)).thenReturn(con(Limite.de(300), null, EstadoSuscripcion.VIGENTE));

        mvc.perform(get("/v1/cuenta").requestAttr(CuentaActual.ATRIBUTO, CUENTA))
                .andExpect(jsonPath("$.datos.uso.api_keys").value(1))
                .andExpect(jsonPath("$.datos.uso.usuarios").value(2))
                .andExpect(jsonPath("$.datos.uso.rucs").value(1));
    }

    @Test void conDocumentosIlimitadosNoHayTope() throws Exception {
        when(miCuenta.deLaCuenta(CUENTA)).thenReturn(con(Limite.sinLimite(), null, EstadoSuscripcion.VIGENTE));

        mvc.perform(get("/v1/cuenta").requestAttr(CuentaActual.ATRIBUTO, CUENTA))
                .andExpect(jsonPath("$.datos.consumo.maximo").doesNotExist())
                .andExpect(jsonPath("$.datos.plan.vence_en").doesNotExist());
    }

    /** Es del portal: con una API key no hay cuenta, solo empresa. */
    @Test void sinSesionDeCuentaEsNoAutorizado() throws Exception {
        mvc.perform(get("/v1/cuenta")).andExpect(status().isUnauthorized());
        verifyNoInteractions(miCuenta);
    }
}
