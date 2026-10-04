package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.EmpresaDetalle;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;
import pe.factura.application.port.in.VisibilidadDeBajas;
import pe.factura.domain.tenant.Entorno;

import java.time.Instant;
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
            EstadoCertificado.POR_VENCER, LocalDate.of(2026, 10, 20), 17, true, 2, 31, LocalDate.of(2026, 10, 2), null);

    @Autowired MockMvc mvc;
    @MockBean ListarEmpresasAdminUseCase listar;
    @MockBean DetalleEmpresaAdminUseCase detalle;

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
                null, null, false, 0, 0, null, null);
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

    // --- #186: detalle ------------------------------------------------------------------------------------------------------------

    static EmpresaDetalle detalleCompleto() {
        UUID comprobante = UUID.fromString("33333333-3333-3333-3333-333333333333");
        var domicilio = new DetalleEmpresaAdminUseCase.DomicilioDeEmpresa("150122", "AV. LARCO 345", "URB. SOL", "MIRAFLORES", "LIMA", "LIMA", "0000");
        return new EmpresaDetalle(ID, "20100066603", "COMERCIAL ANDINA SAC", "ANDINA", Entorno.PRODUCCION, Instant.parse("2026-09-01T10:00:00Z"), CUENTA, "Mi negocio",
                EstadoCertificado.POR_VENCER, LocalDate.of(2026, 10, 13), 10, true, domicilio, "00-123-456789", true,
                new DetalleEmpresaAdminUseCase.PdfDeEmpresa("MODERNO", "#0F766E", true, "Gracias", "Pago a 30 días"),
                List.of(new DetalleEmpresaAdminUseCase.SerieDeEmpresa("01", "F001", 12, true, "0000")),
                List.of(new DetalleEmpresaAdminUseCase.EstablecimientoDeEmpresa("Tienda Surco",
                        new DetalleEmpresaAdminUseCase.DomicilioDeEmpresa("150140", "AV. CAMINOS 100", null, "SURCO", "LIMA", "LIMA", "0002"), true)),
                List.of(new DetalleEmpresaAdminUseCase.ApiKeyDeEmpresa(UUID.fromString("44444444-4444-4444-4444-444444444444"), "fk_demo001", false,
                        Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-09-20T12:00:00Z"))),
                List.of(new DetalleEmpresaAdminUseCase.ComprobanteReciente(comprobante, "01", "F001", 12, LocalDate.of(2026, 9, 30), "ACEPTADO_CON_OBS", "PEN",
                        new java.math.BigDecimal("118.00"), 2, "timeout de SUNAT",
                        new DetalleEmpresaAdminUseCase.Cdr("0", "La Factura ha sido aceptada", List.of("4287 - El dato ingresado no cumple")))),
                List.of(new DetalleEmpresaAdminUseCase.EventoDeComprobante("F001", 12, "FIRMADO", "ACEPTADO_CON_OBS", "CDR recibido", Instant.parse("2026-09-30T15:00:00Z"))),
                new DetalleEmpresaAdminUseCase.Outbox(12, List.of(new DetalleEmpresaAdminUseCase.TareaPendiente("DOCUMENTO", comprobante, "ENVIAR", 3,
                        Instant.parse("2026-10-01T10:00:00Z"), "SUNAT no responde"))));
    }

    @Test void abreElDetalleDeUnaEmpresaConTodoLoQueVeSuDueno() throws Exception {
        when(detalle.detalle(ID)).thenReturn(detalleCompleto());

        mvc.perform(get("/v1/admin/empresas/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.razon_social").value("COMERCIAL ANDINA SAC"))
                .andExpect(jsonPath("$.datos.nombre_comercial").value("ANDINA"))
                .andExpect(jsonPath("$.datos.entorno").value("PRODUCCION"))
                .andExpect(jsonPath("$.datos.creada_en").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.datos.cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos.cuenta_nombre").value("Mi negocio"))
                .andExpect(jsonPath("$.datos.certificado").value("POR_VENCER"))
                .andExpect(jsonPath("$.datos.certificado_vigente_hasta").value("2026-10-13"))
                .andExpect(jsonPath("$.datos.certificado_dias_restantes").value(10))
                .andExpect(jsonPath("$.datos.tiene_credenciales_sol").value(true))
                .andExpect(jsonPath("$.datos.domicilio.ubigeo").value("150122"))
                .andExpect(jsonPath("$.datos.domicilio.direccion").value("AV. LARCO 345"))
                .andExpect(jsonPath("$.datos.domicilio.codigo_establecimiento").value("0000"))
                .andExpect(jsonPath("$.datos.cuenta_detracciones").value("00-123-456789"))
                .andExpect(jsonPath("$.datos.padron_tasa_especial_igv").value(true))
                .andExpect(jsonPath("$.datos.pdf.plantilla").value("MODERNO"))
                .andExpect(jsonPath("$.datos.pdf.color_primario").value("#0F766E"))
                .andExpect(jsonPath("$.datos.pdf.tiene_logo").value(true))
                .andExpect(jsonPath("$.datos.pdf.pie_de_pagina").value("Gracias"))
                .andExpect(jsonPath("$.datos.pdf.observaciones_por_defecto").value("Pago a 30 días"))
                .andExpect(jsonPath("$.datos.series[0].codigo").value("F001"))
                .andExpect(jsonPath("$.datos.series[0].ultimo_numero").value(12))
                .andExpect(jsonPath("$.datos.series[0].establecimiento").value("0000"))
                .andExpect(jsonPath("$.datos.establecimientos[0].nombre").value("Tienda Surco"))
                .andExpect(jsonPath("$.datos.establecimientos[0].codigo").value("0002"))
                .andExpect(jsonPath("$.datos.establecimientos[0].domicilio.distrito").value("SURCO"))
                .andExpect(jsonPath("$.datos.establecimientos[0].activo").value(true))
                .andExpect(jsonPath("$.datos.api_keys[0].prefijo").value("fk_demo001"))
                .andExpect(jsonPath("$.datos.api_keys[0].activa").value(false))
                .andExpect(jsonPath("$.datos.api_keys[0].revocada_en").value("2026-09-20T12:00:00Z"))
                .andExpect(jsonPath("$.datos.comprobantes[0].serie").value("F001"))
                .andExpect(jsonPath("$.datos.comprobantes[0].numero").value(12))
                .andExpect(jsonPath("$.datos.comprobantes[0].estado").value("ACEPTADO_CON_OBS"))
                .andExpect(jsonPath("$.datos.comprobantes[0].total").value(118.00))
                .andExpect(jsonPath("$.datos.comprobantes[0].intentos").value(2))
                .andExpect(jsonPath("$.datos.comprobantes[0].ultimo_error").value("timeout de SUNAT"))
                .andExpect(jsonPath("$.datos.comprobantes[0].cdr.codigo").value("0"))
                .andExpect(jsonPath("$.datos.comprobantes[0].cdr.descripcion").value("La Factura ha sido aceptada"))
                .andExpect(jsonPath("$.datos.comprobantes[0].cdr.observaciones[0]").value("4287 - El dato ingresado no cumple"))
                .andExpect(jsonPath("$.datos.eventos[0].comprobante").value("F001-00000012"))
                .andExpect(jsonPath("$.datos.eventos[0].estado_anterior").value("FIRMADO"))
                .andExpect(jsonPath("$.datos.eventos[0].estado_nuevo").value("ACEPTADO_CON_OBS"))
                .andExpect(jsonPath("$.datos.eventos[0].detalle").value("CDR recibido"))
                .andExpect(jsonPath("$.datos.outbox.total").value(12))
                .andExpect(jsonPath("$.datos.outbox.proximas[0].accion").value("ENVIAR"))
                .andExpect(jsonPath("$.datos.outbox.proximas[0].intentos").value(3))
                .andExpect(jsonPath("$.datos.outbox.proximas[0].ultimo_error").value("SUNAT no responde"));
    }

    /** Solo lectura y sin secretos: ni el hash de una API key, ni dónde está guardado el logo, ni nada del certificado o de la clave SOL. */
    @Test void elDetalleNoExponeSecretos() throws Exception {
        when(detalle.detalle(ID)).thenReturn(detalleCompleto());

        String cuerpo = mvc.perform(get("/v1/admin/empresas/" + ID)).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("hash", "logo_key", "pkcs12", "key_hash", "password", "sol_usuario", "clave_sol", "secreto");
    }

    /** Como en toda la API (`non_null`): lo que no tiene valor no aparece, y las listas vacías sí salen vacías. */
    @Test void unaEmpresaSinNadaCargadoNoTraeCamposVaciosYSusListasSalenVacias() throws Exception {
        EmpresaDetalle nueva = new EmpresaDetalle(ID, "20100066611", "INTEGRADOR SAC", null, Entorno.BETA, Instant.parse("2026-09-01T10:00:00Z"), null, null,
                EstadoCertificado.SIN_CERTIFICADO, null, null, false, null, null, false,
                new DetalleEmpresaAdminUseCase.PdfDeEmpresa("CLASICO", "#1E1E24", false, null, null), List.of(), List.of(), List.of(), List.of(), List.of(),
                new DetalleEmpresaAdminUseCase.Outbox(0, List.of()));
        when(detalle.detalle(ID)).thenReturn(nueva);

        mvc.perform(get("/v1/admin/empresas/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.certificado").value("SIN_CERTIFICADO"))
                .andExpect(jsonPath("$.datos.nombre_comercial").doesNotExist())
                .andExpect(jsonPath("$.datos.cuenta_id").doesNotExist())
                .andExpect(jsonPath("$.datos.cuenta_nombre").doesNotExist())
                .andExpect(jsonPath("$.datos.certificado_vigente_hasta").doesNotExist())
                .andExpect(jsonPath("$.datos.domicilio").doesNotExist())
                .andExpect(jsonPath("$.datos.pdf.tiene_logo").value(false))
                .andExpect(jsonPath("$.datos.pdf.pie_de_pagina").doesNotExist())
                .andExpect(jsonPath("$.datos.series").isEmpty())
                .andExpect(jsonPath("$.datos.establecimientos").isEmpty())
                .andExpect(jsonPath("$.datos.api_keys").isEmpty())
                .andExpect(jsonPath("$.datos.comprobantes").isEmpty())
                .andExpect(jsonPath("$.datos.eventos").isEmpty())
                .andExpect(jsonPath("$.datos.outbox.total").value(0))
                .andExpect(jsonPath("$.datos.outbox.proximas").isEmpty());
    }

    @Test void unComprobanteSinCdrNoTraeElCdr() throws Exception {
        var base = detalleCompleto();
        var sinCdr = new DetalleEmpresaAdminUseCase.ComprobanteReciente(UUID.randomUUID(), "01", "F001", 13, LocalDate.of(2026, 10, 1), "FIRMADO", "PEN",
                new java.math.BigDecimal("50.00"), 0, null, null);
        when(detalle.detalle(ID)).thenReturn(new EmpresaDetalle(base.id(), base.ruc(), base.razonSocial(), base.nombreComercial(), base.entorno(), base.creadaEn(),
                base.cuentaId(), base.cuentaNombre(), base.certificado(), base.certificadoVigenteHasta(), base.certificadoDiasRestantes(), base.tieneCredencialesSol(),
                base.domicilio(), base.cuentaDetracciones(), base.padronTasaEspecialIgv(), base.pdf(), base.series(), base.establecimientos(), base.apiKeys(),
                List.of(sinCdr), base.eventos(), base.outbox()));

        mvc.perform(get("/v1/admin/empresas/" + ID))
                .andExpect(jsonPath("$.datos.comprobantes[0].estado").value("FIRMADO"))
                .andExpect(jsonPath("$.datos.comprobantes[0].cdr").doesNotExist())
                .andExpect(jsonPath("$.datos.comprobantes[0].ultimo_error").doesNotExist());
    }

    @Test void unaEmpresaQueNoExisteEs404() throws Exception {
        when(detalle.detalle(ID)).thenThrow(new pe.factura.domain.DomainException("NO_ENCONTRADO", "La empresa no existe"));

        mvc.perform(get("/v1/admin/empresas/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(get("/v1/admin/empresas/no-es-un-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(detalle);
    }

    // --- #201: baja lógica de la cuenta ----------------------------------------------------------------------------------------------

    @Test void unaEmpresaDeUnaCuentaDeBajaDiceDesdeCuando() throws Exception {
        EmpresaResumen deBaja = new EmpresaResumen(ID, "20100066603", "COMERCIAL ANDINA SAC", CUENTA, "Mi negocio", Entorno.PRODUCCION,
                EstadoCertificado.POR_VENCER, LocalDate.of(2026, 10, 20), 17, true, 2, 31, LocalDate.of(2026, 10, 2), java.time.Instant.parse("2026-10-03T09:00:00Z"));
        when(listar.listar(new Filtro(null, null, VisibilidadDeBajas.SOLO), 1, 20)).thenReturn(List.of(deBaja));

        mvc.perform(get("/v1/admin/empresas").param("bajas", "SOLO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].cuenta_de_baja_en").value("2026-10-03T09:00:00Z"));
    }

    @Test void unaEmpresaEnServicioNoLlevaFechaDeBaja() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANDINA));

        mvc.perform(get("/v1/admin/empresas")).andExpect(jsonPath("$.datos[0].cuenta_de_baja_en").doesNotExist());
    }

    @Test void sinPedirNadaLasBajasSeOcultanYElTotalLasTrataIgual() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANDINA));

        mvc.perform(get("/v1/admin/empresas")).andExpect(status().isOk());

        verify(listar).listar(new Filtro(null, null, VisibilidadDeBajas.OCULTAS), 1, 20);
        verify(listar).contar(new Filtro(null, null, VisibilidadDeBajas.OCULTAS));
    }

    @Test void elFiltroDeBajasSeCombinaConLosOtrosYLlegaAlTotal() throws Exception {
        var filtro = new Filtro(Entorno.PRODUCCION, EstadoCertificado.VENCIDO, VisibilidadDeBajas.INCLUIDAS);
        when(listar.listar(filtro, 1, 20)).thenReturn(List.of(ANDINA));
        when(listar.contar(filtro)).thenReturn(4L);

        mvc.perform(get("/v1/admin/empresas").param("entorno", "PRODUCCION").param("certificado", "VENCIDO").param("bajas", "INCLUIDAS"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "4"));
    }

    @Test void unValorDeBajasQueNoExisteEs400() throws Exception {
        mvc.perform(get("/v1/admin/empresas").param("bajas", "TODAS")).andExpect(status().isBadRequest());

        verifyNoInteractions(listar);
    }
}
