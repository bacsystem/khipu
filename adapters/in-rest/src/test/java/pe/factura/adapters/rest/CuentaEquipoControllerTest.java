package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.factura.application.port.in.GestionarEquipoUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Invitacion;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #21: el equipo de la cuenta desde el portal. */
@WebMvcTest(controllers = CuentaEquipoController.class)
@Import(GlobalExceptionHandler.class)
class CuentaEquipoControllerTest {
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID YO = UUID.randomUUID();
    static final String PORTAL = "http://localhost:3000";

    @Autowired MockMvc mvc;
    @MockitoBean GestionarEquipoUseCase equipo;

    final Usuario ana = new Usuario(YO, CUENTA, "ana@torres.pe", "hash", Rol.ADMIN, true, Instant.parse("2026-10-01T00:00:00Z"));
    final Usuario beto = new Usuario(UUID.randomUUID(), CUENTA, "beto@torres.pe", "hash", Rol.EMISOR, false);
    final Invitacion caro = Invitacion.nueva(CUENTA, "caro@torres.pe", Rol.LECTURA, "hash-secreto", Instant.parse("2026-10-20T12:00:00Z"), Duration.ofDays(3650), YO);

    private MockHttpServletRequestBuilder conSesion(MockHttpServletRequestBuilder b) {
        return b.requestAttr(CuentaActual.ATRIBUTO, CUENTA).requestAttr(UsuarioActual.ATRIBUTO, YO).contentType("application/json");
    }

    @Test void listaLosUsuariosDeLaCuentaYMarcaElDeLaSesion() throws Exception {
        when(equipo.miembros(CUENTA, YO)).thenReturn(List.of(ana, beto));

        mvc.perform(conSesion(get("/v1/cuenta/usuarios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].email").value("ana@torres.pe"))
                .andExpect(jsonPath("$.datos[0].rol").value("ADMIN"))
                .andExpect(jsonPath("$.datos[0].es_usted").value(true))
                .andExpect(jsonPath("$.datos[0].correo_verificado").value(true))
                .andExpect(jsonPath("$.datos[1].rol").value("EMISOR"))
                .andExpect(jsonPath("$.datos[1].activo").value(false))
                .andExpect(jsonPath("$.datos[1].es_usted").value(false))
                .andExpect(jsonPath("$.datos[0].password_hash").doesNotExist());
    }

    @Test void cambiaElRol() throws Exception {
        when(equipo.cambiarRol(CUENTA, YO, beto.id(), Rol.LECTURA)).thenReturn(beto.conRol(Rol.LECTURA));

        mvc.perform(conSesion(put("/v1/cuenta/usuarios/{id}/rol", beto.id())).content("{\"rol\":\"LECTURA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("LECTURA"));
        verify(equipo).cambiarRol(CUENTA, YO, beto.id(), Rol.LECTURA);
    }

    @Test void unRolQueNoExisteEs422SinLlegarAlServicio() throws Exception {
        mvc.perform(conSesion(put("/v1/cuenta/usuarios/{id}/rol", beto.id())).content("{\"rol\":\"DUENO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores.rol").exists());
        verifyNoInteractions(equipo);
    }

    @Test void elUltimoAdministradorEs409() throws Exception {
        when(equipo.cambiarRol(CUENTA, YO, YO, Rol.EMISOR)).thenThrow(new DomainException("ULTIMO_ADMINISTRADOR", "es el único administrador"));

        mvc.perform(conSesion(put("/v1/cuenta/usuarios/{id}/rol", YO)).content("{\"rol\":\"EMISOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ULTIMO_ADMINISTRADOR"));
    }

    @Test void desactivaYReactiva() throws Exception {
        when(equipo.desactivar(CUENTA, YO, beto.id())).thenReturn(beto.desactivar());
        when(equipo.reactivar(CUENTA, YO, beto.id())).thenReturn(beto.reactivar());

        mvc.perform(conSesion(post("/v1/cuenta/usuarios/{id}/desactivar", beto.id()))).andExpect(status().isOk()).andExpect(jsonPath("$.datos.activo").value(false));
        mvc.perform(conSesion(post("/v1/cuenta/usuarios/{id}/reactivar", beto.id()))).andExpect(status().isOk()).andExpect(jsonPath("$.datos.activo").value(true));
    }

    /** Segunda barrera: si el filtro dejara pasar a quien no es administrador, el servicio lo rechaza con el mismo código. */
    @Test void sinRolDeAdministradorEs403() throws Exception {
        when(equipo.miembros(CUENTA, YO)).thenThrow(new DomainException("ROL_INSUFICIENTE", "Solo un administrador"));

        mvc.perform(conSesion(get("/v1/cuenta/usuarios"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"));
    }

    @Test void invitarDevuelve201SinElHashDelToken() throws Exception {
        when(equipo.invitar(CUENTA, YO, "caro@torres.pe", Rol.LECTURA, PORTAL)).thenReturn(new GestionarEquipoUseCase.Enviada(caro, true));

        mvc.perform(conSesion(post("/v1/cuenta/invitaciones")).content("{\"email\":\"caro@torres.pe\",\"rol\":\"LECTURA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.invitacion.email").value("caro@torres.pe"))
                .andExpect(jsonPath("$.datos.invitacion.rol").value("LECTURA"))
                .andExpect(jsonPath("$.datos.invitacion.vencida").value(false))
                .andExpect(jsonPath("$.datos.invitacion.envios").value(1))
                .andExpect(jsonPath("$.datos.correo_enviado").value(true))
                .andExpect(jsonPath("$.datos.invitacion.token_hash").doesNotExist());
    }

    @Test void invitarConDatosInvalidosEs422() throws Exception {
        mvc.perform(conSesion(post("/v1/cuenta/invitaciones")).content("{\"email\":\"no-es-correo\",\"rol\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores.email").exists())
                .andExpect(jsonPath("$.errores.rol").exists());
        verifyNoInteractions(equipo);
    }

    @Test void losConflictosDeLaInvitacionSon409YLosTopes429() throws Exception {
        for (String codigo : new String[]{"YA_ES_MIEMBRO", "USUARIO_EXISTENTE", "INVITACION_PENDIENTE"}) {
            reset(equipo);
            when(equipo.invitar(any(), any(), any(), any(), any())).thenThrow(new DomainException(codigo, "no"));
            mvc.perform(conSesion(post("/v1/cuenta/invitaciones")).content("{\"email\":\"caro@torres.pe\",\"rol\":\"EMISOR\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value(codigo));
        }
        reset(equipo);
        when(equipo.invitar(any(), any(), any(), any(), any())).thenThrow(new DomainException("DEMASIADAS_INVITACIONES", "no"));
        mvc.perform(conSesion(post("/v1/cuenta/invitaciones")).content("{\"email\":\"caro@torres.pe\",\"rol\":\"EMISOR\"}")).andExpect(status().isTooManyRequests());
    }

    @Test void listaReenviaYRevocaInvitaciones() throws Exception {
        when(equipo.invitaciones(CUENTA, YO)).thenReturn(List.of(caro));
        when(equipo.reenviar(CUENTA, YO, caro.id(), PORTAL)).thenReturn(new GestionarEquipoUseCase.Enviada(caro, false));

        mvc.perform(conSesion(get("/v1/cuenta/invitaciones"))).andExpect(status().isOk()).andExpect(jsonPath("$.datos[0].id").value(caro.id().toString()));
        mvc.perform(conSesion(post("/v1/cuenta/invitaciones/{id}/reenviar", caro.id()))).andExpect(status().isOk()).andExpect(jsonPath("$.datos.correo_enviado").value(false));
        mvc.perform(conSesion(delete("/v1/cuenta/invitaciones/{id}", caro.id()))).andExpect(status().isNoContent());
        verify(equipo).revocar(CUENTA, YO, caro.id());
    }

    @Test void unaInvitacionVencidaSeMarca() throws Exception {
        Invitacion vencida = Invitacion.nueva(CUENTA, "dani@torres.pe", Rol.EMISOR, "h", Instant.parse("2020-01-01T00:00:00Z"), Duration.ofDays(7), YO);
        when(equipo.invitaciones(CUENTA, YO)).thenReturn(List.of(vencida));

        mvc.perform(conSesion(get("/v1/cuenta/invitaciones"))).andExpect(jsonPath("$.datos[0].vencida").value(true));
    }

    /** Es del portal: con una API key hay empresa pero no cuenta ni usuario. */
    @Test void sinSesionDeCuentaEsNoAutorizado() throws Exception {
        mvc.perform(get("/v1/cuenta/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/cuenta/invitaciones").contentType("application/json").content("{\"email\":\"caro@torres.pe\",\"rol\":\"EMISOR\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(equipo);
    }
}
