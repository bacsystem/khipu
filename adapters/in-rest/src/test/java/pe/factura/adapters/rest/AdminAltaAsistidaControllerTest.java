package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AltaAsistidaUseCase;
import pe.factura.application.port.in.AltaAsistidaUseCase.AltaCreada;
import pe.factura.application.port.in.AltaAsistidaUseCase.Solicitud;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import pe.factura.application.port.in.Idempotencia;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAltaAsistidaController.class, properties = "app.portal-url=https://portal.khipu.test",
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminAltaAsistidaControllerTest {
    static final String CUERPO = """
            {"nombre":"Comercial Andina","email":"ana@andina.pe","telefono":"987654321",
             "empresa":{"ruc":"20100066603","razon_social":"COMERCIAL ANDINA SAC","entorno":"BETA"},
             "serie":{"tipo":"01","serie":"F001"}}""";
    static final Solicitud SOLICITUD = new Solicitud("Comercial Andina", "ana@andina.pe", "987654321", "20100066603", "COMERCIAL ANDINA SAC", Entorno.BETA, TipoDocumento.FACTURA, "F001");
    static final ActorAdmin CLAVE = ActorAdmin.clavePlataforma("127.0.0.1");   // IP por defecto de MockMvc
    static final String PORTAL = "https://portal.khipu.test";

    @Autowired MockMvc mvc;
    @MockBean AltaAsistidaUseCase alta;

    static AltaCreada creada(boolean invitacionEnviada) {
        Tenant t = new Tenant(UUID.fromString("5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f"), "20100066603", "COMERCIAL ANDINA SAC", Entorno.BETA, null, null);
        return new AltaCreada(UUID.fromString("0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11"), t, "fk_secreta", TipoDocumento.FACTURA, "F001", invitacionEnviada);
    }

    @Test void creaYDevuelve201ConLaApiKeyYElEstadoDeLaInvitacion() throws Exception {
        when(alta.alta(CLAVE, SOLICITUD, PORTAL)).thenReturn(creada(true));
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("exito"))
                .andExpect(jsonPath("$.datos.cuenta_id").value("0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11"))
                .andExpect(jsonPath("$.datos.tenant_id").value("5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f"))
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.api_key").value("fk_secreta"))
                .andExpect(jsonPath("$.datos.serie.tipo").value("01"))
                .andExpect(jsonPath("$.datos.serie.serie").value("F001"))
                .andExpect(jsonPath("$.datos.invitacion_enviada").value(true));
    }

    // --- #219: Idempotency-Key ---------------------------------------------------------------------------------------------------

    static final String CLAVE_IDEM = "a1b2c3d4-0000-4000-8000-000000000001";

    private org.springframework.test.web.servlet.ResultActions conClave(String clave) throws Exception {
        return mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(CUERPO).header("Idempotency-Key", clave)
                .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE));
    }

    @Test void conClaveElAltaNuevaEs201YLaRepetidaEs200ConLaMismaApiKey() throws Exception {
        when(alta.alta(eq(CLAVE), eq(SOLICITUD), eq(PORTAL), any())).thenReturn(new AltaAsistidaUseCase.Resultado(creada(true), false));
        conClave(CLAVE_IDEM).andExpect(status().isCreated()).andExpect(jsonPath("$.datos.api_key").value("fk_secreta"));

        when(alta.alta(eq(CLAVE), eq(SOLICITUD), eq(PORTAL), any())).thenReturn(new AltaAsistidaUseCase.Resultado(creada(true), true));
        conClave(CLAVE_IDEM).andExpect(status().isOk()).andExpect(jsonPath("$.datos.api_key").value("fk_secreta"));

        org.mockito.ArgumentCaptor<Idempotencia> cap = org.mockito.ArgumentCaptor.forClass(Idempotencia.class);
        verify(alta, org.mockito.Mockito.times(2)).alta(eq(CLAVE), eq(SOLICITUD), eq(PORTAL), cap.capture());
        assertThat(cap.getValue().clave()).isEqualTo(CLAVE_IDEM);
        assertThat(cap.getValue().huella()).matches("[0-9a-f]{64}");
        verify(alta, never()).alta(any(), any(), any());
    }

    @Test void unaClaveMalFormadaEs422SinDarDeAlta() throws Exception {
        conClave("corta").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_INVALIDA"));
        verify(alta, never()).alta(any(), any(), any(), any());
    }

    @Test void unaRespuestaYaOlvidadaEs409() throws Exception {
        when(alta.alta(eq(CLAVE), eq(SOLICITUD), eq(PORTAL), any())).thenThrow(new DomainException("IDEMPOTENCIA_VENCIDA", "ya no se puede mostrar"));
        conClave(CLAVE_IDEM).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_VENCIDA"));
    }

    @Test void siElCorreoNoSalioLoDiceYEntregaLaApiKeyIgual() throws Exception {
        when(alta.alta(CLAVE, SOLICITUD, PORTAL)).thenReturn(creada(false));
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.invitacion_enviada").value(false))
                .andExpect(jsonPath("$.datos.api_key").value("fk_secreta"));
    }

    @Test void quedaAtribuidoAlAdministradorQueTieneSesionDesdeSuIp() throws Exception {
        UUID quien = UUID.randomUUID();
        ActorAdmin actor = ActorAdmin.administrador(quien, "203.0.113.7");
        when(alta.alta(actor, SOLICITUD, PORTAL)).thenReturn(creada(true));
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO, quien).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isCreated());
        verify(alta).alta(actor, SOLICITUD, PORTAL);
    }

    @Test void sinEntornoYConUnaBoletaPasaLoQueSePidio() throws Exception {
        Solicitud boleta = new Solicitud("Comercial Andina", "ana@andina.pe", null, "20100066603", "COMERCIAL ANDINA SAC", null, TipoDocumento.BOLETA, "B001");
        when(alta.alta(CLAVE, boleta, PORTAL)).thenReturn(creada(true));
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content("""
                        {"nombre":"Comercial Andina","email":"ana@andina.pe",
                         "empresa":{"ruc":"20100066603","razon_social":"COMERCIAL ANDINA SAC"},
                         "serie":{"tipo":"03","serie":"B001"}}""")
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isCreated());
        verify(alta).alta(CLAVE, boleta, PORTAL);
    }

    @Test void elAdministradorNoPuedeFijarUnaContrasena() throws Exception {
        // Una contraseña en el cuerpo se ignora: la solicitud no tiene dónde llevarla, y el cliente fija la suya con la invitación.
        when(alta.alta(CLAVE, SOLICITUD, PORTAL)).thenReturn(creada(true));
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json")
                        .content(CUERPO.replace("{\"nombre\"", "{\"password\":\"Segura123\",\"nombre\""))
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isCreated());
        verify(alta).alta(CLAVE, SOLICITUD, PORTAL);
    }

    @Test void sinCredencialNoCreaNadaYEs401() throws Exception {
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
        verify(alta, never()).alta(any(), any(), any());
    }

    @Test void unCorreoOUnaEmpresaYaRegistradosSon409() throws Exception {
        when(alta.alta(CLAVE, SOLICITUD, PORTAL)).thenThrow(new DomainException("DUPLICADO", "Ya existe una cuenta con ese correo"));
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test void unaSerieQueNoEsDelTipoEs422() throws Exception {
        when(alta.alta(CLAVE, SOLICITUD, PORTAL)).thenThrow(new DomainException("SERIE_INVALIDA", "Serie F001 no válida para BOLETA"));
        mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SERIE_INVALIDA"));
    }

    @Test void cuerpoIncompletoOMalFormadoEs422SinLlamarAlCasoDeUso() throws Exception {
        String[] cuerpos = {
                "{}",
                CUERPO.replace("\"nombre\":\"Comercial Andina\"", "\"nombre\":\"\""),
                CUERPO.replace("ana@andina.pe", ""),
                CUERPO.replace("20100066603", "123"),
                // Más largos que las columnas (cuenta.nombre 150, cuenta.email 254): sin tope serían un 500 de la base y no un 422.
                CUERPO.replace("Comercial Andina\"", "x".repeat(151) + "\""),
                CUERPO.replace("ana@andina.pe", "a".repeat(250) + "@andina.pe"),
                CUERPO.replace("\"tipo\":\"01\"", "\"tipo\":\"99\""),
                CUERPO.replace("\"serie\":{\"tipo\":\"01\",\"serie\":\"F001\"}", "\"serie\":null"),
                CUERPO.replace("\"empresa\":{\"ruc\":\"20100066603\",\"razon_social\":\"COMERCIAL ANDINA SAC\",\"entorno\":\"BETA\"},", "")
        };
        for (String c : cuerpos) {
            mvc.perform(post("/v1/admin/cuentas").contentType("application/json").content(c)
                            .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                    .andExpect(status().isUnprocessableEntity());
        }
        verify(alta, never()).alta(any(), any(), any());
    }
}
