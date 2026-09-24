package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AdministrarTenantUseCase;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.*;

import pe.factura.domain.DomainException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {EmpresaController.class, AdminTenantController.class, AdminIntegridadController.class}, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class EmpresaControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AdministrarTenantUseCase admin;
    @MockBean pe.factura.application.port.in.VerificarIntegridadUseCase integridad;
    UUID tenant = UUID.randomUUID();
    UUID cuenta = UUID.randomUUID();

    @Test void verEmpresaSinSecretos() throws Exception {
        when(admin.obtener(tenant)).thenReturn(new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA,
                new CredencialesSol("MODDATOS", "moddatos"), new CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1))));
        mvc.perform(get("/v1/empresa").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.tiene_credenciales_sol").value(true))
                .andExpect(jsonPath("$.datos.certificado_vigencia_hasta").value("2030-01-01"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("moddatos"))));
    }

    @Test void datosFiscalesEntranYSalen() throws Exception {
        Tenant con = new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA, null, null)
                .conDatosFiscales(new Domicilio("150122", "Av. Larco 345 Of. 12", null, null, null, null, null), "00-000-123456", "Andina Store", true);
        when(admin.actualizarDatosFiscales(eq(tenant), any(), eq("00-000-123456"), eq("Andina Store"), eq(true))).thenReturn(con);
        mvc.perform(put("/v1/empresa/datos-fiscales").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"domicilio\":{\"ubigeo\":\"150122\",\"direccion\":\"Av. Larco 345 Of. 12\"},\"cuenta_detracciones\":\"00-000-123456\",\"nombre_comercial\":\"Andina Store\",\"padron_tasa_especial_igv\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombre_comercial").value("Andina Store"))
                .andExpect(jsonPath("$.datos.padron_tasa_especial_igv").value(true))
                .andExpect(jsonPath("$.datos.domicilio.ubigeo").value("150122"))
                .andExpect(jsonPath("$.datos.domicilio.distrito").value("MIRAFLORES"))
                .andExpect(jsonPath("$.datos.domicilio.codigo_establecimiento").value("0000"))
                .andExpect(jsonPath("$.datos.cuenta_detracciones").value("00-000-123456"));
        verify(admin).actualizarDatosFiscales(eq(tenant), argThat(d -> d.ubigeo().equals("150122") && d.provincia().equals("LIMA")), eq("00-000-123456"), eq("Andina Store"), eq(true));
    }

    @Test void datosFiscalesConUbigeoInexistenteEs422() throws Exception {
        mvc.perform(put("/v1/empresa/datos-fiscales").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"domicilio\":{\"ubigeo\":\"999999\",\"direccion\":\"Av. Larco 345\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DOMICILIO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("4093")));
        // Solo la cuenta (domicilio null): el DTO no exige el bloque.
        // Sin padron_tasa_especial_igv en el body → false (tasa general).
        when(admin.actualizarDatosFiscales(eq(tenant), isNull(), eq("00-000-1"), isNull(), eq(false))).thenReturn(new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA, null, null, null, "00-000-1"));
        mvc.perform(put("/v1/empresa/datos-fiscales").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"cuenta_detracciones\":\"00-000-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.domicilio").doesNotExist())
                .andExpect(jsonPath("$.datos.cuenta_detracciones").value("00-000-1"))
                .andExpect(jsonPath("$.datos.padron_tasa_especial_igv").value(false));
    }

    @Test void subirCertificado() throws Exception {
        mvc.perform(multipart("/v1/empresa/certificado").file(new MockMultipartFile("archivo", "c.pfx", "application/x-pkcs12", new byte[]{1, 2}))
                        .param("clave", "test1234").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isNoContent());
        verify(admin).cargarCertificado(eq(tenant), eq(new byte[]{1, 2}), eq("test1234"));
    }

    @Test void credencialesSol() throws Exception {
        mvc.perform(put("/v1/empresa/credenciales-sol").contentType("application/json").content("{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isNoContent());
        verify(admin).cargarCredencialesSol(tenant, "MODDATOS", "moddatos");
    }

    @Test void crearSerieYListar() throws Exception {
        when(admin.listarSeries(tenant)).thenReturn(List.of(new Serie(tenant, TipoDocumento.FACTURA, "F001", 0, true)));
        mvc.perform(post("/v1/series").contentType("application/json").content("{\"tipo\":\"01\",\"serie\":\"F001\",\"correlativo_inicial\":0}").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isCreated());
        verify(admin).crearSerie(tenant, TipoDocumento.FACTURA, "F001", 0, null);
        mvc.perform(get("/v1/series").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos[0].serie").value("F001")).andExpect(jsonPath("$.datos[0].tipo").value("01"))
                .andExpect(jsonPath("$.datos[0].establecimiento").value("0000"));
        // Serie asignada a un anexo (#80).
        mvc.perform(post("/v1/series").contentType("application/json").content("{\"tipo\":\"01\",\"serie\":\"F002\",\"establecimiento\":\"0002\"}").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isCreated());
        verify(admin).crearSerie(tenant, TipoDocumento.FACTURA, "F002", 0, "0002");
    }

    /** Una serie repetida y un documento repetido caen las dos en la UNIQUE: el mensaje tiene que distinguirlas. */
    @Test void serieRepetidaEs409ConSuPropioMensaje() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("ERROR: duplicate key value violates unique constraint \"serie_tenant_id_tipo_codigo_key\""))
                .when(admin).crearSerie(tenant, TipoDocumento.FACTURA, "F001", 0, null);
        mvc.perform(post("/v1/series").contentType("application/json").content("{\"tipo\":\"01\",\"serie\":\"F001\",\"correlativo_inicial\":0}").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe una serie con ese tipo y código"));
    }

    /** El correlativo son 8 dígitos (regla 1001): el DTO lo corta antes de llegar al caso de uso. */
    @Test void correlativoDeMasDeOchoDigitosEs422() throws Exception {
        mvc.perform(post("/v1/series").contentType("application/json").content("{\"tipo\":\"01\",\"serie\":\"F001\",\"correlativo_inicial\":100000000}").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        org.mockito.Mockito.verify(admin, org.mockito.Mockito.never()).crearSerie(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    /** Sin handler propio, un archivo grande caía en el catch-all y devolvía 500 INTERNO. */
    @Test void certificadoDemasiadoGrandeEs422YNo500() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(1_048_576L))
                .when(admin).cargarCertificado(org.mockito.ArgumentMatchers.eq(tenant), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        mvc.perform(multipart("/v1/empresa/certificado").file(new MockMultipartFile("archivo", "c.p12", "application/x-pkcs12", new byte[]{1, 2}))
                        .param("clave", "x").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_DEMASIADO_GRANDE"));
    }

    @Test void establecimientosAnexos() throws Exception {
        Domicilio fiscal = Domicilio.de("150101", "Av. Lima 123");
        Domicilio larco = Domicilio.de("150122", "Av. Larco 345");
        Establecimiento tienda = new Establecimiento(tenant, "0002", "Tienda Miraflores", larco, true);
        when(admin.obtener(tenant)).thenReturn(new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA, null, null).conDatosFiscales(fiscal, null, null));
        when(admin.listarEstablecimientos(tenant)).thenReturn(List.of(tienda, tienda.desactivar().con("Cerrada", larco, false)));
        // La lista arranca con el 0000 (domicilio fiscal, principal) y sigue con los anexos.
        mvc.perform(get("/v1/empresa/establecimientos").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].codigo").value("0000")).andExpect(jsonPath("$.datos[0].principal").value(true))
                .andExpect(jsonPath("$.datos[0].domicilio.direccion").value("Av. Lima 123"))
                .andExpect(jsonPath("$.datos[1].codigo").value("0002")).andExpect(jsonPath("$.datos[1].nombre").value("Tienda Miraflores"))
                .andExpect(jsonPath("$.datos[1].domicilio.distrito").value("MIRAFLORES")).andExpect(jsonPath("$.datos[1].activo").value(true))
                .andExpect(jsonPath("$.datos[2].activo").value(false));

        when(admin.guardarEstablecimiento(eq(tenant), eq("0002"), eq("Tienda Miraflores"), any())).thenReturn(tienda);
        mvc.perform(post("/v1/empresa/establecimientos").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"codigo\":\"0002\",\"nombre\":\"Tienda Miraflores\",\"domicilio\":{\"ubigeo\":\"150122\",\"direccion\":\"Av. Larco 345\"}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.datos.codigo").value("0002")).andExpect(jsonPath("$.datos.principal").value(false));
        verify(admin).guardarEstablecimiento(eq(tenant), eq("0002"), eq("Tienda Miraflores"), argThat(d -> d.ubigeo().equals("150122")));

        // Código de 4 dígitos (3030) y domicilio obligatorios.
        mvc.perform(post("/v1/empresa/establecimientos").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"codigo\":\"2\",\"nombre\":\"X\",\"domicilio\":{\"ubigeo\":\"150122\",\"direccion\":\"Av. Larco 345\"}}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/empresa/establecimientos").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"codigo\":\"0003\",\"nombre\":\"X\"}"))
                .andExpect(status().isUnprocessableEntity());

        // PUT: el código de la ruta manda; baja lógica con 204; el 0000 no se toca; en uso → 409.
        mvc.perform(put("/v1/empresa/establecimientos/0002").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"codigo\":\"0009\",\"nombre\":\"X\",\"domicilio\":{\"ubigeo\":\"150122\",\"direccion\":\"Av. Larco 345\"}}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("ESTABLECIMIENTO_INVALIDO"));
        when(admin.desactivarEstablecimiento(tenant, "0002")).thenReturn(tienda.desactivar());
        mvc.perform(delete("/v1/empresa/establecimientos/0002").requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isNoContent());
        mvc.perform(delete("/v1/empresa/establecimientos/0000").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("domicilio fiscal")));
        when(admin.desactivarEstablecimiento(tenant, "0003")).thenThrow(new DomainException("ESTABLECIMIENTO_EN_USO", "tiene series activas (F003)"));
        mvc.perform(delete("/v1/empresa/establecimientos/0003").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTABLECIMIENTO_EN_USO"));
    }

    @Test void crearApiKey() throws Exception {
        when(admin.crearApiKey(tenant)).thenReturn("fk_nueva");
        mvc.perform(post("/v1/empresa/api-keys").requestAttr(TenantActual.ATRIBUTO, tenant).requestAttr(CuentaActual.ATRIBUTO, cuenta))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.datos.api_key").value("fk_nueva"));
    }

    @Test void listarApiKeysSinHash() throws Exception {
        UUID id = UUID.randomUUID();
        Instant creada = Instant.parse("2026-09-15T10:00:00Z");
        when(admin.listarApiKeys(tenant)).thenReturn(List.of(
                new ApiKey(id, tenant, "hash-secreto", "fk_abcdefg", true, creada, null),
                new ApiKey(UUID.randomUUID(), tenant, "otro-hash", "fk_hijklmn", false, creada, Instant.parse("2026-09-15T12:00:00Z"))));
        mvc.perform(get("/v1/empresa/api-keys").requestAttr(TenantActual.ATRIBUTO, tenant).requestAttr(CuentaActual.ATRIBUTO, cuenta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].id").value(id.toString()))
                .andExpect(jsonPath("$.datos[0].prefijo").value("fk_abcdefg"))
                .andExpect(jsonPath("$.datos[0].activa").value(true))
                .andExpect(jsonPath("$.datos[0].creada_en").value("2026-09-15T10:00:00Z"))
                .andExpect(jsonPath("$.datos[0].revocada_en").doesNotExist())
                .andExpect(jsonPath("$.datos[1].activa").value(false))
                .andExpect(jsonPath("$.datos[1].revocada_en").value("2026-09-15T12:00:00Z"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hash"))));
    }

    @Test void revocarApiKey() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/v1/empresa/api-keys/" + id).requestAttr(TenantActual.ATRIBUTO, tenant).requestAttr(CuentaActual.ATRIBUTO, cuenta))
                .andExpect(status().isNoContent());
        verify(admin).revocarApiKey(tenant, id);
    }

    /** Con solo API key (tenant sin cuenta) la gestión de keys se rechaza: una key filtrada no puede crear otras ni revocar las del tenant. */
    @Test void gestionDeApiKeysConApiKeyEs403() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/v1/empresa/api-keys").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("REQUIERE_SESION"));
        mvc.perform(get("/v1/empresa/api-keys").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("REQUIERE_SESION"));
        mvc.perform(delete("/v1/empresa/api-keys/" + id).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("REQUIERE_SESION"));
        verify(admin, never()).crearApiKey(any());
        verify(admin, never()).listarApiKeys(any());
        verify(admin, never()).revocarApiKey(any(), any());
    }

    @Test void revocarApiKeyAjenaEs404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DomainException("NO_ENCONTRADO", "API key no encontrada")).when(admin).revocarApiKey(tenant, id);
        mvc.perform(delete("/v1/empresa/api-keys/" + id).requestAttr(TenantActual.ATRIBUTO, tenant).requestAttr(CuentaActual.ATRIBUTO, cuenta))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    /** Integridad del storage (#38): el informe del rango vuelve con sus problemas; el rango es obligatorio. */
    @Test void adminVerificaLaIntegridadDelStorage() throws Exception {
        var informe = new pe.factura.application.port.in.VerificarIntegridadUseCase.Informe(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 12,
                List.of(new pe.factura.application.port.in.VerificarIntegridadUseCase.Problema(UUID.randomUUID(), tenant, "20100066603-01-F001-7", "XML_FALTANTE", "t/2026/09/x.xml")));
        when(integridad.verificar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(informe);
        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.verificados").value(12))
                .andExpect(jsonPath("$.datos.problemas[0].tipo").value("XML_FALTANTE"))
                .andExpect(jsonPath("$.datos.problemas[0].nombre_archivo").value("20100066603-01-F001-7"));
        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test void adminCreaTenant() throws Exception {
        Tenant t = new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA, null, null);
        when(admin.crearTenant("20100066603", "EMPRESA SAC", Entorno.BETA)).thenReturn(new AdministrarTenantUseCase.TenantCreado(t, "fk_primera"));
        mvc.perform(post("/v1/admin/tenants").contentType("application/json").content("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA SAC\",\"entorno\":\"BETA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tenant_id").value(tenant.toString()))
                .andExpect(jsonPath("$.datos.api_key").value("fk_primera"));
    }
}
