package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.CuentaDetalle;
import pe.factura.application.port.in.ListarCuentasAdminUseCase;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;
import pe.factura.application.port.in.SuspenderCuentaUseCase;
import pe.factura.application.port.in.VisibilidadDeBajas;
import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCuentaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminCuentaControllerTest {
    static final UUID ID = UUID.randomUUID();
    static final CuentaResumen ANA = new CuentaResumen(ID, "Mi negocio", "ana@negocio.pe", "987654321",
            Instant.parse("2026-09-01T10:00:00Z"), 2, Instant.parse("2026-10-01T09:00:00Z"), null, null);

    @Autowired MockMvc mvc;
    @MockBean ListarCuentasAdminUseCase listar;
    @MockBean DetalleCuentaAdminUseCase detalle;
    @MockBean SuspenderCuentaUseCase suspender;

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
        // El plan llega con #189: hasta entonces no se expone con un valor fijo.
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANA));

        mvc.perform(get("/v1/admin/cuentas"))
                .andExpect(jsonPath("$.datos[0].plan").doesNotExist());
    }

    // --- #182: estado de la cuenta -------------------------------------------------------------------------------------------------

    @Test void unaCuentaActivaDiceActivaYSinFechaDeSuspension() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANA));

        mvc.perform(get("/v1/admin/cuentas"))
                .andExpect(jsonPath("$.datos[0].estado").value("ACTIVA"))
                .andExpect(jsonPath("$.datos[0].suspendida_en").doesNotExist());
    }

    @Test void unaCuentaSuspendidaDiceDesdeCuando() throws Exception {
        CuentaResumen suspendida = new CuentaResumen(ID, "Mi negocio", "ana@negocio.pe", null, Instant.parse("2026-09-01T10:00:00Z"), 1, null, Instant.parse("2026-10-02T15:00:00Z"), null);
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(suspendida));

        mvc.perform(get("/v1/admin/cuentas"))
                .andExpect(jsonPath("$.datos[0].estado").value("SUSPENDIDA"))
                .andExpect(jsonPath("$.datos[0].suspendida_en").value("2026-10-02T15:00:00Z"));
    }

    @Test void suspenderPasaElActorYElMotivoYDevuelveElNuevoEstado() throws Exception {
        UUID admin = UUID.randomUUID();
        var actor = pe.factura.domain.plataforma.ActorAdmin.administrador(admin, "203.0.113.7");
        when(suspender.suspender(actor, ID, "no pagó septiembre")).thenReturn(new SuspenderCuentaUseCase.EstadoDeCuenta(ID, Instant.parse("2026-10-02T15:00:00Z")));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/suspender").contentType("application/json").content("{\"motivo\":\"no pagó septiembre\"}")
                        .requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.estado").value("SUSPENDIDA"))
                .andExpect(jsonPath("$.datos.suspendida_en").value("2026-10-02T15:00:00Z"));
    }

    @Test void suspenderSinCuerpoEsValidoYNoLlevaMotivo() throws Exception {
        when(suspender.suspender(pe.factura.domain.plataforma.ActorAdmin.clavePlataforma("127.0.0.1"), ID, null))
                .thenReturn(new SuspenderCuentaUseCase.EstadoDeCuenta(ID, Instant.parse("2026-10-02T15:00:00Z")));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/suspender").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("SUSPENDIDA"));
    }

    @Test void reactivarDevuelveLaCuentaActivaSinFechaDeSuspension() throws Exception {
        when(suspender.reactivar(pe.factura.domain.plataforma.ActorAdmin.clavePlataforma("127.0.0.1"), ID)).thenReturn(new SuspenderCuentaUseCase.EstadoDeCuenta(ID, null));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reactivar").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.datos.suspendida_en").doesNotExist())
                .andExpect(jsonPath("$.datos.baja_en").doesNotExist());
    }

    /** La baja manda (#201): suspender o reactivar una cuenta de baja responde BAJA, el mismo estado que el detalle y el listado. */
    @Test void suspenderOReactivarUnaCuentaDeBajaRespondeBaja() throws Exception {
        Instant baja = Instant.parse("2026-10-03T09:00:00Z");
        var clave = pe.factura.domain.plataforma.ActorAdmin.clavePlataforma("127.0.0.1");
        when(suspender.suspender(clave, ID, null)).thenReturn(new SuspenderCuentaUseCase.EstadoDeCuenta(ID, Instant.parse("2026-10-04T15:00:00Z"), baja));
        when(suspender.reactivar(clave, ID)).thenReturn(new SuspenderCuentaUseCase.EstadoDeCuenta(ID, null, baja));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/suspender").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(jsonPath("$.datos.estado").value("BAJA"))
                .andExpect(jsonPath("$.datos.suspendida_en").value("2026-10-04T15:00:00Z"))
                .andExpect(jsonPath("$.datos.baja_en").value("2026-10-03T09:00:00Z"));
        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reactivar").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(jsonPath("$.datos.estado").value("BAJA"))
                .andExpect(jsonPath("$.datos.baja_en").value("2026-10-03T09:00:00Z"));
    }

    @Test void suspenderUnaCuentaYaSuspendidaEsConflicto() throws Exception {
        when(suspender.suspender(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(ID), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DomainException("CUENTA_YA_SUSPENDIDA", "La cuenta ya está suspendida"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/suspender").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUENTA_YA_SUSPENDIDA"));
    }

    @Test void reactivarUnaCuentaActivaEsConflicto() throws Exception {
        when(suspender.reactivar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(ID)))
                .thenThrow(new DomainException("CUENTA_NO_SUSPENDIDA", "La cuenta no está suspendida"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reactivar").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUENTA_NO_SUSPENDIDA"));
    }

    @Test void unMotivoDemasiadoLargoEsUnDatoInvalido() throws Exception {
        when(suspender.suspender(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(ID), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DomainException("MOTIVO_INVALIDO", "El motivo no puede pasar de 200 caracteres"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/suspender").contentType("application/json").content("{\"motivo\":\"x\"}")
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
    }

    @Test void unaCuentaQueNoExisteEsNoEncontradaAlSuspender() throws Exception {
        when(suspender.suspender(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(ID), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DomainException("NO_ENCONTRADO", "La cuenta no existe"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/suspender").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isNotFound());
    }

    @Test void sinAdministradorNiClaveAutenticadosLaAccionNoSeEjecuta() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/" + ID + "/suspender")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reactivar")).andExpect(status().isUnauthorized());

        verifyNoInteractions(suspender);
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinSuspenderNiReactivar() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/no-es-un-uuid/suspender").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE)).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/cuentas/no-es-un-uuid/reactivar").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE)).andExpect(status().isBadRequest());

        verifyNoInteractions(suspender);
    }

    /** Como en toda la API (`default-property-inclusion: non_null`), un campo sin valor no aparece: el portal lo trata como opcional. */
    @Test void unaCuentaQueNuncaInicioSesionNoTraElUltimoAcceso() throws Exception {
        CuentaResumen nunca = new CuentaResumen(ID, "Nueva", "nueva@x.pe", null, Instant.parse("2026-09-01T10:00:00Z"), 0, null, null, null);
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

    // --- #181: detalle ------------------------------------------------------------------------------------------------------------

    static CuentaDetalle detalleCompleto() {
        UUID empresa = UUID.randomUUID();
        return new CuentaDetalle(ID, "Mi negocio", "ana@negocio.pe", "987654321", Instant.parse("2026-09-01T10:00:00Z"), null, null,
                List.of(new DetalleCuentaAdminUseCase.UsuarioDeCuenta(UUID.fromString("11111111-1111-1111-1111-111111111111"), "ana@negocio.pe", "ADMIN", true,
                        Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"))),
                List.of(new DetalleCuentaAdminUseCase.EmpresaDeCuenta(empresa, "20100066603", "COMERCIAL ANDINA SAC", "PRODUCCION", true,
                        java.time.LocalDate.of(2027, 3, 1), true)),
                List.of(new DetalleCuentaAdminUseCase.ComprobanteReciente(UUID.fromString("22222222-2222-2222-2222-222222222222"), empresa, "20100066603", "01", "F001", 15,
                        java.time.LocalDate.of(2026, 9, 30), "ACEPTADO", "PEN", new java.math.BigDecimal("118.00"))),
                List.of(new DetalleCuentaAdminUseCase.EventoReciente("CREAR_CUENTA", "ADMINISTRADOR", Instant.parse("2026-09-01T10:00:05Z"), "ruc=20100066603 serie=F001")));
    }

    @Test void abreElDetalleDeUnaCuentaConTodoLoQueMuestraElPanel() throws Exception {
        when(detalle.detalle(ID)).thenReturn(detalleCompleto());

        mvc.perform(get("/v1/admin/cuentas/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.nombre").value("Mi negocio"))
                .andExpect(jsonPath("$.datos.email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos.telefono").value("987654321"))
                .andExpect(jsonPath("$.datos.creada_en").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.datos.usuarios[0].email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos.usuarios[0].rol").value("ADMIN"))
                .andExpect(jsonPath("$.datos.usuarios[0].activo").value(true))
                .andExpect(jsonPath("$.datos.usuarios[0].correo_verificado_en").value("2026-09-02T10:00:00Z"))
                .andExpect(jsonPath("$.datos.usuarios[0].ultimo_acceso").value("2026-10-01T09:00:00Z"))
                .andExpect(jsonPath("$.datos.empresas[0].ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.empresas[0].razon_social").value("COMERCIAL ANDINA SAC"))
                .andExpect(jsonPath("$.datos.empresas[0].entorno").value("PRODUCCION"))
                .andExpect(jsonPath("$.datos.empresas[0].tiene_certificado").value(true))
                .andExpect(jsonPath("$.datos.empresas[0].certificado_vigente_hasta").value("2027-03-01"))
                .andExpect(jsonPath("$.datos.empresas[0].tiene_credenciales_sol").value(true))
                .andExpect(jsonPath("$.datos.comprobantes[0].ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.comprobantes[0].serie").value("F001"))
                .andExpect(jsonPath("$.datos.comprobantes[0].numero").value(15))
                .andExpect(jsonPath("$.datos.comprobantes[0].fecha_emision").value("2026-09-30"))
                .andExpect(jsonPath("$.datos.comprobantes[0].estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.datos.comprobantes[0].total").value(118.00))
                .andExpect(jsonPath("$.datos.eventos[0].accion").value("CREAR_CUENTA"))
                .andExpect(jsonPath("$.datos.eventos[0].actor").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.datos.eventos[0].detalle").value("ruc=20100066603 serie=F001"));
    }

    /** Solo lectura y sin secretos: nada de lo que podría filtrarse (claves, hashes, IP del administrador) sale en el detalle. */
    @Test void elDetalleNoExponeSecretosNiLaIpDelAdministrador() throws Exception {
        when(detalle.detalle(ID)).thenReturn(detalleCompleto());

        String json = mvc.perform(get("/v1/admin/cuentas/{id}", ID)).andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("hash").doesNotContainIgnoringCase("clave")
                .doesNotContainIgnoringCase("secret").doesNotContain("\"ip\"").doesNotContain("administrador_id");
    }

    @Test void unaCuentaSinHistoriaDevuelveListasVaciasYSinCamposOpcionales() throws Exception {
        when(detalle.detalle(ID)).thenReturn(new CuentaDetalle(ID, "Nueva", "nueva@x.pe", null, Instant.parse("2026-09-01T10:00:00Z"), null, null,
                List.of(new DetalleCuentaAdminUseCase.UsuarioDeCuenta(UUID.randomUUID(), "nueva@x.pe", "ADMIN", true, null, null)),
                List.of(new DetalleCuentaAdminUseCase.EmpresaDeCuenta(UUID.randomUUID(), "20100066611", "VACIA SAC", "BETA", false, null, false)),
                List.of(), List.of()));

        mvc.perform(get("/v1/admin/cuentas/{id}", ID))
                .andExpect(jsonPath("$.datos.telefono").doesNotExist())
                .andExpect(jsonPath("$.datos.usuarios[0].correo_verificado_en").doesNotExist())
                .andExpect(jsonPath("$.datos.usuarios[0].ultimo_acceso").doesNotExist())
                .andExpect(jsonPath("$.datos.empresas[0].certificado_vigente_hasta").doesNotExist())
                .andExpect(jsonPath("$.datos.empresas[0].tiene_certificado").value(false))
                .andExpect(jsonPath("$.datos.empresas[0].tiene_credenciales_sol").value(false))
                .andExpect(jsonPath("$.datos.comprobantes").isEmpty())
                .andExpect(jsonPath("$.datos.eventos").isEmpty());
    }

    @Test void elDetalleDiceElEstadoDeLaCuentaYDesdeCuandoEstaSuspendida() throws Exception {
        var activa = detalleCompleto();
        when(detalle.detalle(ID)).thenReturn(activa);
        mvc.perform(get("/v1/admin/cuentas/" + ID))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.datos.suspendida_en").doesNotExist());

        var suspendida = new CuentaDetalle(activa.id(), activa.nombre(), activa.email(), activa.telefono(), activa.creadaEn(), Instant.parse("2026-10-02T15:00:00Z"), null,
                activa.usuarios(), activa.empresas(), activa.comprobantes(), activa.eventos());
        when(detalle.detalle(ID)).thenReturn(suspendida);
        mvc.perform(get("/v1/admin/cuentas/" + ID))
                .andExpect(jsonPath("$.datos.estado").value("SUSPENDIDA"))
                .andExpect(jsonPath("$.datos.suspendida_en").value("2026-10-02T15:00:00Z"));
    }

    @Test void unaCuentaQueNoExisteEs404() throws Exception {
        when(detalle.detalle(ID)).thenThrow(new DomainException("NO_ENCONTRADO", "La cuenta no existe"));

        mvc.perform(get("/v1/admin/cuentas/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(get("/v1/admin/cuentas/no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(detalle);
    }

    // --- #201: baja lógica -----------------------------------------------------------------------------------------------------------

    static final Instant SUSPENDIDA_EN = Instant.parse("2026-10-02T15:00:00Z");
    static final Instant BAJA_EN = Instant.parse("2026-10-03T09:00:00Z");

    @Test void unaCuentaDeBajaDiceBajaYDesdeCuando() throws Exception {
        CuentaResumen deBaja = new CuentaResumen(ID, "Mi negocio", "ana@negocio.pe", null, Instant.parse("2026-09-01T10:00:00Z"), 1, null, null, BAJA_EN);
        when(listar.listar(new Filtro(null, VisibilidadDeBajas.SOLO), 1, 20)).thenReturn(List.of(deBaja));

        mvc.perform(get("/v1/admin/cuentas").param("bajas", "SOLO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].estado").value("BAJA"))
                .andExpect(jsonPath("$.datos[0].baja_en").value("2026-10-03T09:00:00Z"));
    }

    @Test void unaCuentaEnServicioNoLlevaFechaDeBaja() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANA));

        mvc.perform(get("/v1/admin/cuentas")).andExpect(jsonPath("$.datos[0].baja_en").doesNotExist());
    }

    /** La baja manda sobre la suspensión en el estado, pero las dos fechas se siguen informando. */
    @Test void unaCuentaDeBajaYSuspendidaDiceBajaYConservaLasDosFechas() throws Exception {
        CuentaResumen doble = new CuentaResumen(ID, "Mi negocio", "ana@negocio.pe", null, Instant.parse("2026-09-01T10:00:00Z"), 1, null, SUSPENDIDA_EN, BAJA_EN);
        when(listar.listar(new Filtro(null, VisibilidadDeBajas.INCLUIDAS), 1, 20)).thenReturn(List.of(doble));

        mvc.perform(get("/v1/admin/cuentas").param("bajas", "INCLUIDAS"))
                .andExpect(jsonPath("$.datos[0].estado").value("BAJA"))
                .andExpect(jsonPath("$.datos[0].suspendida_en").value("2026-10-02T15:00:00Z"))
                .andExpect(jsonPath("$.datos[0].baja_en").value("2026-10-03T09:00:00Z"));
    }

    @Test void sinPedirNadaLasBajasSeOcultanYElTotalLasTrataIgual() throws Exception {
        when(listar.listar(Filtro.NINGUNO, 1, 20)).thenReturn(List.of(ANA));
        when(listar.contar(Filtro.NINGUNO)).thenReturn(1L);

        mvc.perform(get("/v1/admin/cuentas")).andExpect(header().string("X-Total-Count", "1"));

        verify(listar).listar(new Filtro(null, VisibilidadDeBajas.OCULTAS), 1, 20);
        verify(listar).contar(new Filtro(null, VisibilidadDeBajas.OCULTAS));
    }

    @Test void elFiltroDeBajasLlegaAlListadoYAlTotalJuntoConLaBusqueda() throws Exception {
        var filtro = new Filtro("sol", VisibilidadDeBajas.INCLUIDAS);
        when(listar.listar(filtro, 2, 10)).thenReturn(List.of(ANA));
        when(listar.contar(filtro)).thenReturn(11L);

        mvc.perform(get("/v1/admin/cuentas").param("q", "sol").param("bajas", "INCLUIDAS").param("pagina", "2").param("por_pagina", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "11"));
    }

    @Test void unValorDeBajasQueNoExisteEs400() throws Exception {
        mvc.perform(get("/v1/admin/cuentas").param("bajas", "TODAS")).andExpect(status().isBadRequest());

        verifyNoInteractions(listar);
    }

    @Test void elDetalleDeUnaCuentaDeBajaDiceBajaYDesdeCuando() throws Exception {
        var activa = detalleCompleto();
        when(detalle.detalle(ID)).thenReturn(new CuentaDetalle(activa.id(), activa.nombre(), activa.email(), activa.telefono(), activa.creadaEn(), null, BAJA_EN,
                activa.usuarios(), activa.empresas(), activa.comprobantes(), activa.eventos()));

        mvc.perform(get("/v1/admin/cuentas/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("BAJA"))
                .andExpect(jsonPath("$.datos.baja_en").value("2026-10-03T09:00:00Z"))
                .andExpect(jsonPath("$.datos.comprobantes").isNotEmpty());
    }
}
