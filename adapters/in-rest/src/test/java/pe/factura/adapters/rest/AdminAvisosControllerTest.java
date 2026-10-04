package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AvisarAlClienteUseCase;
import pe.factura.application.port.in.AvisarAlClienteUseCase.AvisoEnviado;
import pe.factura.application.port.in.ConsultarAvisosUseCase;
import pe.factura.application.port.in.ConsultarAvisosUseCase.CertificadoEnRiesgo;
import pe.factura.application.port.in.ConsultarAvisosUseCase.CuentaDelCliente;
import pe.factura.application.port.in.ConsultarAvisosUseCase.PaginaDeCertificados;
import pe.factura.application.port.in.ConsultarAvisosUseCase.PaginaDeSol;
import pe.factura.application.port.in.ConsultarAvisosUseCase.SolFallando;
import pe.factura.application.port.in.ConsultarAvisosUseCase.UltimoAviso;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.TipoDeAviso;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los avisos a los clientes desde el backoffice (#197). El portal escribe sus tipos a mano a partir de este JSON: lo que se fija acá es su **forma real** (snake_case, lo opcional
 * ausente en lugar de nulo, el total en la cabecera) y el status de cada error del dominio.
 */
@WebMvcTest(controllers = AdminAvisosController.class, properties = "app.portal-url=https://portal.khipu.test",
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminAvisosControllerTest {
    static final UUID EMPRESA = UUID.randomUUID();
    static final UUID CUENTA = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockBean ConsultarAvisosUseCase consultar;
    @MockBean AvisarAlClienteUseCase avisar;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    static CertificadoEnRiesgo certificado(CuentaDelCliente cuenta, UltimoAviso ultimo, Instant desde, boolean puede) {
        return new CertificadoEnRiesgo(EMPRESA, "20100066603", "COMERCIAL ANDINA SAC", cuenta, MotivoDeAviso.CERTIFICADO_POR_VENCER, LocalDate.of(2026, 10, 25), 10, ultimo, desde, puede);
    }

    // --- certificados -------------------------------------------------------------------------------------------------------------------

    @Test void cadaCertificadoDiceLaEmpresaElMotivoLosDiasLaCuentaYElUltimoAvisoEnSnakeCase() throws Exception {
        when(consultar.certificados(1, 20)).thenReturn(new PaginaDeCertificados(List.of(
                certificado(new CuentaDelCliente(CUENTA, "Ana Pérez", "ana@negocio.pe"), new UltimoAviso(Instant.parse("2026-10-10T15:00:00Z"), "ana@negocio.pe"), Instant.parse("2026-10-17T15:00:00Z"), false)), 37));

        mvc.perform(get("/v1/admin/avisos/certificados").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "37"))
                .andExpect(jsonPath("$.datos.length()").value(1))
                .andExpect(jsonPath("$.datos[0].empresa_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos[0].ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos[0].razon_social").value("COMERCIAL ANDINA SAC"))
                .andExpect(jsonPath("$.datos[0].cuenta.id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos[0].cuenta.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.datos[0].cuenta.email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos[0].motivo").value("CERTIFICADO_POR_VENCER"))
                .andExpect(jsonPath("$.datos[0].vigente_hasta").value("2026-10-25"))
                .andExpect(jsonPath("$.datos[0].dias_restantes").value(10))
                .andExpect(jsonPath("$.datos[0].ultimo_aviso.enviado_en").value("2026-10-10T15:00:00Z"))
                .andExpect(jsonPath("$.datos[0].ultimo_aviso.destinatario").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos[0].avisar_desde").value("2026-10-17T15:00:00Z"))
                .andExpect(jsonPath("$.datos[0].puede_avisar").value(false));
    }

    /** El portal distingue «no hay dato» de «vacío»: lo que no existe no viaja como nulo. */
    @Test void loOpcionalVaAusenteEnLugarDeNulo() throws Exception {
        when(consultar.certificados(1, 20)).thenReturn(new PaginaDeCertificados(List.of(certificado(null, null, null, false)), 1));

        mvc.perform(get("/v1/admin/avisos/certificados").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].cuenta").doesNotExist())
                .andExpect(jsonPath("$.datos[0].ultimo_aviso").doesNotExist())
                .andExpect(jsonPath("$.datos[0].avisar_desde").doesNotExist())
                .andExpect(jsonPath("$.datos[0].puede_avisar").value(false));
    }

    @Test void unaListaVaciaEsUnaListaVaciaConElTotalEnCero() throws Exception {
        when(consultar.certificados(1, 20)).thenReturn(new PaginaDeCertificados(List.of(), 0));

        mvc.perform(get("/v1/admin/avisos/certificados").with(clave()))
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.datos.length()").value(0));
    }

    @Test void noTraeMasCamposQueLosQueElPortalConoce() throws Exception {
        when(consultar.certificados(1, 20)).thenReturn(new PaginaDeCertificados(List.of(certificado(new CuentaDelCliente(CUENTA, "Ana", "ana@negocio.pe"), null, null, true)), 1));

        mvc.perform(get("/v1/admin/avisos/certificados").with(clave()))
                .andExpect(jsonPath("$.datos[0].empresaId").doesNotExist())
                .andExpect(jsonPath("$.datos[0].razonSocial").doesNotExist())
                .andExpect(jsonPath("$.datos[0].diasRestantes").doesNotExist())
                .andExpect(jsonPath("$.datos[0].vigenteHasta").doesNotExist())
                .andExpect(jsonPath("$.datos[0].puedeAvisar").doesNotExist());
    }

    @Test void laPaginaYElTamanoLlegamAlCasoDeUsoYSeAcotan() throws Exception {
        when(consultar.certificados(any(Integer.class), any(Integer.class))).thenReturn(new PaginaDeCertificados(List.of(), 0));

        mvc.perform(get("/v1/admin/avisos/certificados").param("pagina", "3").param("por_pagina", "50").with(clave())).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/avisos/certificados").param("pagina", "0").param("por_pagina", "0").with(clave())).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/avisos/certificados").param("pagina", "-4").param("por_pagina", "1000").with(clave())).andExpect(status().isOk());

        verify(consultar).certificados(3, 50);
        verify(consultar).certificados(1, 1);
        verify(consultar).certificados(1, 100);
    }

    // --- credenciales SOL ------------------------------------------------------------------------------------------------------------------

    @Test void cadaEmpresaConLaSolRechazadaDiceCuantosComprobantesTieneAtascadosYQueDijoSunat() throws Exception {
        when(consultar.credencialesSol(1, 20)).thenReturn(new PaginaDeSol(List.of(new SolFallando(EMPRESA, "20100066603", "COMERCIAL ANDINA SAC", new CuentaDelCliente(CUENTA, "Ana", "ana@negocio.pe"), 4,
                Instant.parse("2026-10-15T14:50:00Z"), "0102 - Usuario o contraseña incorrectos", null, null, true)), 3));

        mvc.perform(get("/v1/admin/avisos/credenciales-sol").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.datos[0].empresa_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos[0].comprobantes_afectados").value(4))
                .andExpect(jsonPath("$.datos[0].ultimo_fallo").value("2026-10-15T14:50:00Z"))
                .andExpect(jsonPath("$.datos[0].ultimo_error").value("0102 - Usuario o contraseña incorrectos"))
                .andExpect(jsonPath("$.datos[0].cuenta.email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos[0].ultimo_aviso").doesNotExist())
                .andExpect(jsonPath("$.datos[0].avisar_desde").doesNotExist())
                .andExpect(jsonPath("$.datos[0].puede_avisar").value(true));
    }

    @Test void laPaginaDeSolLlegaAlCasoDeUsoYSeAcota() throws Exception {
        when(consultar.credencialesSol(any(Integer.class), any(Integer.class))).thenReturn(new PaginaDeSol(List.of(), 0));

        mvc.perform(get("/v1/admin/avisos/credenciales-sol").param("pagina", "2").param("por_pagina", "10").with(clave())).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/avisos/credenciales-sol").param("pagina", "0").param("por_pagina", "500").with(clave())).andExpect(status().isOk());

        verify(consultar).credencialesSol(2, 10);
        verify(consultar).credencialesSol(1, 100);
    }

    // --- avisar -------------------------------------------------------------------------------------------------------------------------

    @Test void avisarDiceElMotivoAQuienSeLeEscribioYDesdeCuandoSePuedeRepetir() throws Exception {
        when(avisar.avisar(any(ActorAdmin.class), eq(EMPRESA), eq(TipoDeAviso.CERTIFICADO), eq("https://portal.khipu.test")))
                .thenReturn(new AvisoEnviado(EMPRESA, MotivoDeAviso.CERTIFICADO_POR_VENCER, "ana@negocio.pe", Instant.parse("2026-10-15T15:00:00Z"), Instant.parse("2026-10-22T15:00:00Z")));

        mvc.perform(post("/v1/admin/empresas/" + EMPRESA + "/avisos").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"CERTIFICADO\"}").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.empresa_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos.motivo").value("CERTIFICADO_POR_VENCER"))
                .andExpect(jsonPath("$.datos.destinatario").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos.enviado_en").value("2026-10-15T15:00:00Z"))
                .andExpect(jsonPath("$.datos.avisar_desde").value("2026-10-22T15:00:00Z"));
    }

    @Test void elAvisoSeMandaConElLinkAlPortalDeLaConfiguracionYQuienLoPidio() throws Exception {
        when(avisar.avisar(any(ActorAdmin.class), any(), any(), any())).thenReturn(new AvisoEnviado(EMPRESA, MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS, "ana@negocio.pe", Instant.now(), Instant.now()));

        mvc.perform(post("/v1/admin/empresas/" + EMPRESA + "/avisos").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"CREDENCIALES_SOL\"}").with(clave())).andExpect(status().isOk());

        ArgumentCaptor<ActorAdmin> actor = ArgumentCaptor.forClass(ActorAdmin.class);
        verify(avisar).avisar(actor.capture(), eq(EMPRESA), eq(TipoDeAviso.CREDENCIALES_SOL), eq("https://portal.khipu.test"));
        org.assertj.core.api.Assertions.assertThat(actor.getValue().tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
    }

    @Test void sinCuerpoOSinTipoElTipoLlegaNuloYElCasoDeUsoLoRechaza() throws Exception {
        when(avisar.avisar(any(ActorAdmin.class), eq(EMPRESA), eq(null), any())).thenThrow(new DomainException("TIPO_INVALIDO", "Indica qué se le avisa al cliente"));

        mvc.perform(post("/v1/admin/empresas/" + EMPRESA + "/avisos").with(clave())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("TIPO_INVALIDO"));
        mvc.perform(post("/v1/admin/empresas/" + EMPRESA + "/avisos").contentType(MediaType.APPLICATION_JSON).content("{}").with(clave()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("TIPO_INVALIDO"));
    }

    @Test void unTipoQueNoExisteOUnCuerpoRotoEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/v1/admin/empresas/" + EMPRESA + "/avisos").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"OTRO\"}").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/empresas/" + EMPRESA + "/avisos").contentType(MediaType.APPLICATION_JSON).content("{no es json").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/empresas/no-es-un-uuid/avisos").contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"CERTIFICADO\"}").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(avisar);
    }

    @Test void losErroresDelDominioTienenSuStatus() throws Exception {
        String cuerpo = "{\"tipo\":\"CERTIFICADO\"}";
        String ruta = "/v1/admin/empresas/" + EMPRESA + "/avisos";
        Object[][] casos = {
                {"NO_ENCONTRADO", 404}, {"AVISO_SIN_MOTIVO", 409}, {"EMPRESA_SIN_CUENTA", 409}, {"AVISO_RECIENTE", 409}, {"CORREO_NO_CONFIGURADO", 503}, {"CORREO_NO_ENVIADO", 502}};
        for (Object[] caso : casos) {
            when(avisar.avisar(any(ActorAdmin.class), eq(EMPRESA), any(), any())).thenThrow(new DomainException((String) caso[0], "x"));
            mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(clave()))
                    .andExpect(status().is((Integer) caso[1])).andExpect(jsonPath("$.codigo").value((String) caso[0]));
        }
    }
}
