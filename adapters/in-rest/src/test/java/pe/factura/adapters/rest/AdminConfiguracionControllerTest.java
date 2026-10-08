package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.BannerPublicado;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.PlantillaEditable;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.RemitenteVigente;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La configuración de la plataforma desde el backoffice (#199). El portal escribe sus tipos a mano a partir de este JSON: lo que se fija acá es su **forma real** (snake_case, lo
 * opcional ausente en lugar de nulo) y el status de cada error del dominio.
 */
@WebMvcTest(controllers = AdminConfiguracionController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminConfiguracionControllerTest {
    static final Instant ACTUALIZADO = Instant.parse("2026-10-15T15:00:00Z");
    static final RemitenteDeCorreo PREDETERMINADO = new RemitenteDeCorreo(null, "no-responder@khipu.pe", null);

    @Autowired MockMvc mvc;
    @MockBean ConfigurarPlataformaUseCase configuracion;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    // --- remitente ----------------------------------------------------------------------------------------------------------------------

    @Test void elRemitenteDiceElVigenteSiEsPersonalizadoYElPredeterminadoEnSnakeCase() throws Exception {
        when(configuracion.remitente()).thenReturn(new RemitenteVigente(new RemitenteDeCorreo("khipu", "avisos@khipu.pe", "soporte@khipu.pe"), true, ACTUALIZADO, PREDETERMINADO));

        mvc.perform(get("/v1/admin/configuracion/correo").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.vigente.nombre").value("khipu"))
                .andExpect(jsonPath("$.datos.vigente.email").value("avisos@khipu.pe"))
                .andExpect(jsonPath("$.datos.vigente.responder_a").value("soporte@khipu.pe"))
                .andExpect(jsonPath("$.datos.personalizado").value(true))
                .andExpect(jsonPath("$.datos.actualizado_en").value("2026-10-15T15:00:00Z"))
                .andExpect(jsonPath("$.datos.predeterminado.email").value("no-responder@khipu.pe"))
                .andExpect(jsonPath("$.datos.predeterminado.nombre").doesNotExist())
                .andExpect(jsonPath("$.datos.predeterminado.responder_a").doesNotExist());
    }

    /** H11: quién lo fijó, no solo «un administrador». */
    @Test void elRemitenteDiceElCorreoDeQuienLoFijo() throws Exception {
        when(configuracion.remitente()).thenReturn(new RemitenteVigente(new RemitenteDeCorreo(null, "avisos@khipu.pe", null), true, ACTUALIZADO, "ana@khipu.pe", PREDETERMINADO));

        mvc.perform(get("/v1/admin/configuracion/correo").with(clave())).andExpect(jsonPath("$.datos.actualizado_por").value("ana@khipu.pe"));
    }

    @Test void sinRemitentePropioNoHayFechaDeCambioNiNombre() throws Exception {
        when(configuracion.remitente()).thenReturn(new RemitenteVigente(PREDETERMINADO, false, null, PREDETERMINADO));

        mvc.perform(get("/v1/admin/configuracion/correo").with(clave()))
                .andExpect(jsonPath("$.datos.personalizado").value(false))
                .andExpect(jsonPath("$.datos.actualizado_en").doesNotExist())
                .andExpect(jsonPath("$.datos.vigente.nombre").doesNotExist());
    }

    @Test void cambiarElRemitenteLlevaLosTresCamposYQuienLoPidio() throws Exception {
        when(configuracion.cambiarRemitente(any(ActorAdmin.class), any(), any(), any())).thenReturn(new RemitenteVigente(new RemitenteDeCorreo("khipu", "avisos@khipu.pe", "soporte@khipu.pe"), true, ACTUALIZADO, PREDETERMINADO));

        mvc.perform(put("/v1/admin/configuracion/correo").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"khipu\",\"email\":\"avisos@khipu.pe\",\"responder_a\":\"soporte@khipu.pe\"}").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.vigente.email").value("avisos@khipu.pe"));

        ArgumentCaptor<ActorAdmin> actor = ArgumentCaptor.forClass(ActorAdmin.class);
        verify(configuracion).cambiarRemitente(actor.capture(), eq("khipu"), eq("avisos@khipu.pe"), eq("soporte@khipu.pe"));
        assertThat(actor.getValue().tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
    }

    @Test void sinCuerpoOSinCorreoLlegaNuloYElCasoDeUsoLoRechazaConUnMensaje() throws Exception {
        when(configuracion.cambiarRemitente(any(ActorAdmin.class), any(), eq(null), any())).thenThrow(new DomainException("REMITENTE_INVALIDO", "El correo del remitente es obligatorio"));

        mvc.perform(put("/v1/admin/configuracion/correo").with(clave())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("REMITENTE_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("El correo del remitente es obligatorio"));
        mvc.perform(put("/v1/admin/configuracion/correo").contentType(MediaType.APPLICATION_JSON).content("{}").with(clave())).andExpect(status().isUnprocessableEntity());
    }

    @Test void restablecerElRemitenteDevuelveElPredeterminado() throws Exception {
        when(configuracion.restablecerRemitente(any(ActorAdmin.class))).thenReturn(new RemitenteVigente(PREDETERMINADO, false, null, PREDETERMINADO));

        mvc.perform(delete("/v1/admin/configuracion/correo").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.personalizado").value(false))
                .andExpect(jsonPath("$.datos.vigente.email").value("no-responder@khipu.pe"));
    }

    // --- plantillas ---------------------------------------------------------------------------------------------------------------------

    static PlantillaEditable editable(PlantillaDeCorreo tipo, boolean personalizada) {
        return new PlantillaEditable(tipo, personalizada ? new Texto("Mi asunto", "Mi cuerpo {enlace}") : tipo.defecto(), tipo.defecto(), personalizada, personalizada ? ACTUALIZADO : null);
    }

    @Test void cadaPlantillaDiceSuTextoElDeFabricaSiSeCambioYSusVariablesEnSnakeCase() throws Exception {
        when(configuracion.plantillas()).thenReturn(List.of(editable(PlantillaDeCorreo.RECUPERACION_CLAVE, true), editable(PlantillaDeCorreo.BIENVENIDA, false)));

        mvc.perform(get("/v1/admin/configuracion/plantillas").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.length()").value(2))
                .andExpect(jsonPath("$.datos[0].tipo").value("RECUPERACION_CLAVE"))
                .andExpect(jsonPath("$.datos[0].etiqueta").value("Restablecer la contraseña"))
                .andExpect(jsonPath("$.datos[0].cuando_se_manda").isNotEmpty())
                .andExpect(jsonPath("$.datos[0].vigente.asunto").value("Mi asunto"))
                .andExpect(jsonPath("$.datos[0].vigente.cuerpo").value("Mi cuerpo {enlace}"))
                .andExpect(jsonPath("$.datos[0].defecto.asunto").value("Restablecer contraseña"))
                .andExpect(jsonPath("$.datos[0].personalizada").value(true))
                .andExpect(jsonPath("$.datos[0].actualizada_en").value("2026-10-15T15:00:00Z"))
                .andExpect(jsonPath("$.datos[0].variables[0].nombre").value("enlace"))
                .andExpect(jsonPath("$.datos[0].variables[0].indispensable").value(true))
                .andExpect(jsonPath("$.datos[0].variables[0].descripcion").isNotEmpty())
                .andExpect(jsonPath("$.datos[0].variables[0].ejemplo").isNotEmpty())
                .andExpect(jsonPath("$.datos[0].variables[1].nombre").value("validez"))
                .andExpect(jsonPath("$.datos[0].variables[1].indispensable").value(false))
                .andExpect(jsonPath("$.datos[1].personalizada").value(false))
                .andExpect(jsonPath("$.datos[1].actualizada_en").doesNotExist())
                .andExpect(jsonPath("$.datos[1].vigente.asunto").value("Te damos la bienvenida a khipu"))
                .andExpect(jsonPath("$.datos[0].cuandoSeManda").doesNotExist());
    }

    @Test void guardarUnaPlantillaLlevaElTipoElAsuntoElCuerpoYQuienLoPidio() throws Exception {
        when(configuracion.guardarPlantilla(any(ActorAdmin.class), eq(PlantillaDeCorreo.RECUPERACION_CLAVE), any(), any())).thenReturn(editable(PlantillaDeCorreo.RECUPERACION_CLAVE, true));

        mvc.perform(put("/v1/admin/configuracion/plantillas/RECUPERACION_CLAVE").contentType(MediaType.APPLICATION_JSON).content("{\"asunto\":\"Mi asunto\",\"cuerpo\":\"Mi cuerpo {enlace}\"}").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.tipo").value("RECUPERACION_CLAVE"))
                .andExpect(jsonPath("$.datos.personalizada").value(true));

        ArgumentCaptor<ActorAdmin> actor = ArgumentCaptor.forClass(ActorAdmin.class);
        verify(configuracion).guardarPlantilla(actor.capture(), eq(PlantillaDeCorreo.RECUPERACION_CLAVE), eq("Mi asunto"), eq("Mi cuerpo {enlace}"));
        assertThat(actor.getValue().tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
    }

    @Test void unaPlantillaInvalidaVuelveConElMensajeDeLoQueHayQueCorregir() throws Exception {
        when(configuracion.guardarPlantilla(any(ActorAdmin.class), any(), any(), any())).thenThrow(new DomainException("PLANTILLA_INVALIDA", "El cuerpo tiene que incluir {enlace}"));

        mvc.perform(put("/v1/admin/configuracion/plantillas/RECUPERACION_CLAVE").contentType(MediaType.APPLICATION_JSON).content("{\"asunto\":\"x\",\"cuerpo\":\"y\"}").with(clave()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLANTILLA_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("El cuerpo tiene que incluir {enlace}"));
    }

    @Test void sinCuerpoLosCamposLleganNulosYElCasoDeUsoLosRechaza() throws Exception {
        when(configuracion.guardarPlantilla(any(ActorAdmin.class), any(), eq(null), eq(null))).thenThrow(new DomainException("PLANTILLA_INVALIDA", "El asunto no puede estar vacío"));

        mvc.perform(put("/v1/admin/configuracion/plantillas/BIENVENIDA").with(clave())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PLANTILLA_INVALIDA"));
    }

    @Test void unCorreoQueNoExisteEs404SinLlamarAlCasoDeUsoEnLasTresRutas() throws Exception {
        for (String tipo : new String[]{"NO_EXISTE", "recuperacion_clave", "Recuperacion_Clave"}) {
            mvc.perform(put("/v1/admin/configuracion/plantillas/" + tipo).contentType(MediaType.APPLICATION_JSON).content("{\"asunto\":\"a\",\"cuerpo\":\"b\"}").with(clave()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
            mvc.perform(delete("/v1/admin/configuracion/plantillas/" + tipo).with(clave())).andExpect(status().isNotFound());
            mvc.perform(post("/v1/admin/configuracion/plantillas/" + tipo + "/vista-previa").contentType(MediaType.APPLICATION_JSON).content("{\"asunto\":\"a\",\"cuerpo\":\"b\"}").with(clave()))
                    .andExpect(status().isNotFound());
        }

        verifyNoInteractions(configuracion);
    }

    @Test void restaurarUnaPlantillaDevuelveElTextoDeFabrica() throws Exception {
        when(configuracion.restaurarPlantilla(any(ActorAdmin.class), eq(PlantillaDeCorreo.BIENVENIDA))).thenReturn(editable(PlantillaDeCorreo.BIENVENIDA, false));

        mvc.perform(delete("/v1/admin/configuracion/plantillas/BIENVENIDA").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.personalizada").value(false))
                .andExpect(jsonPath("$.datos.vigente.asunto").value("Te damos la bienvenida a khipu"));
    }

    @Test void laVistaPreviaDevuelveElAsuntoYElCuerpoYaConLosEjemplos() throws Exception {
        when(configuracion.vistaPrevia(PlantillaDeCorreo.RECUPERACION_CLAVE, "Hola", "Entra a {enlace}")).thenReturn(new Texto("Hola", "Entra a https://app.khipu.pe/restablecer/0a1b2c3d"));

        mvc.perform(post("/v1/admin/configuracion/plantillas/RECUPERACION_CLAVE/vista-previa").contentType(MediaType.APPLICATION_JSON).content("{\"asunto\":\"Hola\",\"cuerpo\":\"Entra a {enlace}\"}").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.asunto").value("Hola"))
                .andExpect(jsonPath("$.datos.cuerpo").value("Entra a https://app.khipu.pe/restablecer/0a1b2c3d"));
    }

    @Test void unaVistaPreviaInvalidaEs422() throws Exception {
        when(configuracion.vistaPrevia(any(), any(), any())).thenThrow(new DomainException("PLANTILLA_INVALIDA", "x"));

        mvc.perform(post("/v1/admin/configuracion/plantillas/BIENVENIDA/vista-previa").contentType(MediaType.APPLICATION_JSON).content("{}").with(clave())).andExpect(status().isUnprocessableEntity());
    }

    @Test void cadaCorreoDeLaListaTieneUnaRutaPropia() throws Exception {
        for (PlantillaDeCorreo tipo : PlantillaDeCorreo.values()) {
            when(configuracion.restaurarPlantilla(any(ActorAdmin.class), eq(tipo))).thenReturn(editable(tipo, false));
            mvc.perform(delete("/v1/admin/configuracion/plantillas/" + tipo.name()).with(clave())).andExpect(status().isOk()).andExpect(jsonPath("$.datos.tipo").value(tipo.name()));
        }
    }

    // --- banner -------------------------------------------------------------------------------------------------------------------------

    static final Instant DESDE = Instant.parse("2026-10-15T20:00:00Z");
    static final Instant HASTA = Instant.parse("2026-10-16T01:00:00Z");

    @Test void sinBannerLosDatosSonNulos() throws Exception {
        when(configuracion.banner()).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/v1/admin/configuracion/banner").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("exito"))
                .andExpect(jsonPath("$.datos").doesNotExist());
    }

    @Test void elBannerDiceSuTextoSuVigenciaYSiSeMuestraAhora() throws Exception {
        when(configuracion.banner()).thenReturn(java.util.Optional.of(new BannerPublicado(new BannerDeMantenimiento("Mantenimiento esta noche", DESDE, HASTA), ACTUALIZADO, false)));

        mvc.perform(get("/v1/admin/configuracion/banner").with(clave()))
                .andExpect(jsonPath("$.datos.texto").value("Mantenimiento esta noche"))
                .andExpect(jsonPath("$.datos.desde").value("2026-10-15T20:00:00Z"))
                .andExpect(jsonPath("$.datos.hasta").value("2026-10-16T01:00:00Z"))
                .andExpect(jsonPath("$.datos.actualizado_en").value("2026-10-15T15:00:00Z"))
                .andExpect(jsonPath("$.datos.vigente_ahora").value(false));
    }

    @Test void publicarUnBannerLlevaTextoYFechasYQuienLoPidio() throws Exception {
        when(configuracion.publicarBanner(any(ActorAdmin.class), any(), any(), any())).thenReturn(new BannerPublicado(new BannerDeMantenimiento("Mantenimiento esta noche", DESDE, HASTA), ACTUALIZADO, true));

        mvc.perform(put("/v1/admin/configuracion/banner").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Mantenimiento esta noche\",\"desde\":\"2026-10-15T20:00:00Z\",\"hasta\":\"2026-10-16T01:00:00Z\"}").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.vigente_ahora").value(true));

        ArgumentCaptor<ActorAdmin> actor = ArgumentCaptor.forClass(ActorAdmin.class);
        verify(configuracion).publicarBanner(actor.capture(), eq("Mantenimiento esta noche"), eq(DESDE), eq(HASTA));
        assertThat(actor.getValue().tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
    }

    @Test void unBannerInvalidoEs422ConSuMensajeYSinCuerpoLosCamposLleganNulos() throws Exception {
        when(configuracion.publicarBanner(any(ActorAdmin.class), eq(null), eq(null), eq(null))).thenThrow(new DomainException("BANNER_INVALIDO", "El texto del aviso no puede estar vacío"));

        mvc.perform(put("/v1/admin/configuracion/banner").with(clave())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("BANNER_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("El texto del aviso no puede estar vacío"));
    }

    @Test void unaFechaQueNoEsUnaFechaEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(put("/v1/admin/configuracion/banner").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"x\",\"desde\":\"mañana\",\"hasta\":\"2026-10-16T01:00:00Z\"}").with(clave()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/configuracion/banner").contentType(MediaType.APPLICATION_JSON).content("{no es json").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(configuracion);
    }

    @Test void retirarElBannerLlevaQuienLoPidio() throws Exception {
        mvc.perform(delete("/v1/admin/configuracion/banner").with(clave())).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("exito"));

        ArgumentCaptor<ActorAdmin> actor = ArgumentCaptor.forClass(ActorAdmin.class);
        verify(configuracion).retirarBanner(actor.capture());
        assertThat(actor.getValue().tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
    }

    @Test void retirarSinBannerEs404() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("NO_ENCONTRADO", "No hay un aviso publicado")).when(configuracion).retirarBanner(any(ActorAdmin.class));

        mvc.perform(delete("/v1/admin/configuracion/banner").with(clave())).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
