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

@WebMvcTest(controllers = {EmpresaController.class, AdminTenantController.class}, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class EmpresaControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AdministrarTenantUseCase admin;
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
                .conDatosFiscales(new Domicilio("150122", "Av. Larco 345 Of. 12", null, null, null, null, null), "00-000-123456");
        when(admin.actualizarDatosFiscales(eq(tenant), any(), eq("00-000-123456"))).thenReturn(con);
        mvc.perform(put("/v1/empresa/datos-fiscales").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"domicilio\":{\"ubigeo\":\"150122\",\"direccion\":\"Av. Larco 345 Of. 12\"},\"cuenta_detracciones\":\"00-000-123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.domicilio.ubigeo").value("150122"))
                .andExpect(jsonPath("$.datos.domicilio.distrito").value("MIRAFLORES"))
                .andExpect(jsonPath("$.datos.domicilio.codigo_establecimiento").value("0000"))
                .andExpect(jsonPath("$.datos.cuenta_detracciones").value("00-000-123456"));
        verify(admin).actualizarDatosFiscales(eq(tenant), argThat(d -> d.ubigeo().equals("150122") && d.provincia().equals("LIMA")), eq("00-000-123456"));
    }

    @Test void datosFiscalesConUbigeoInexistenteEs422() throws Exception {
        mvc.perform(put("/v1/empresa/datos-fiscales").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"domicilio\":{\"ubigeo\":\"999999\",\"direccion\":\"Av. Larco 345\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DOMICILIO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("4093")));
        // Solo la cuenta (domicilio null): el DTO no exige el bloque.
        when(admin.actualizarDatosFiscales(eq(tenant), isNull(), eq("00-000-1"))).thenReturn(new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA, null, null, null, "00-000-1"));
        mvc.perform(put("/v1/empresa/datos-fiscales").contentType("application/json").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .content("{\"cuenta_detracciones\":\"00-000-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.domicilio").doesNotExist())
                .andExpect(jsonPath("$.datos.cuenta_detracciones").value("00-000-1"));
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
        verify(admin).crearSerie(tenant, TipoDocumento.FACTURA, "F001", 0);
        mvc.perform(get("/v1/series").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos[0].serie").value("F001")).andExpect(jsonPath("$.datos[0].tipo").value("01"));
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

    @Test void adminCreaTenant() throws Exception {
        Tenant t = new Tenant(tenant, "20100066603", "EMPRESA SAC", Entorno.BETA, null, null);
        when(admin.crearTenant("20100066603", "EMPRESA SAC", Entorno.BETA)).thenReturn(new AdministrarTenantUseCase.TenantCreado(t, "fk_primera"));
        mvc.perform(post("/v1/admin/tenants").contentType("application/json").content("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA SAC\",\"entorno\":\"BETA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tenant_id").value(tenant.toString()))
                .andExpect(jsonPath("$.datos.api_key").value("fk_primera"));
    }
}
