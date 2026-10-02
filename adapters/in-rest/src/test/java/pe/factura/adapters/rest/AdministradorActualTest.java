package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministradorActualTest {
    private static MockHttpServletRequest peticion() {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants");
        req.setRemoteAddr("203.0.113.7");
        return req;
    }

    @Test void conSesionDeAdministradorElActorEsEseAdministradorDesdeSuIp() {
        UUID id = UUID.randomUUID();
        var req = peticion();
        req.setAttribute(AdministradorActual.ATRIBUTO, id);

        ActorAdmin actor = AdministradorActual.actor(req);

        assertThat(actor.tipo()).isEqualTo(ActorAdmin.Tipo.ADMINISTRADOR);
        assertThat(actor.administradorId()).isEqualTo(id);
        assertThat(actor.ip()).isEqualTo("203.0.113.7");
    }

    @Test void conClaveDePlataformaElActorEsLaClaveDesdeSuIp() {
        var req = peticion();
        req.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE);

        ActorAdmin actor = AdministradorActual.actor(req);

        assertThat(actor.tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
        assertThat(actor.administradorId()).isNull();
        assertThat(actor.ip()).isEqualTo("203.0.113.7");
    }

    @Test void sinNingunaCredencialNoSeInventaUnActor() {
        assertThatThrownBy(() -> AdministradorActual.actor(peticion()))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_AUTORIZADO");
    }
}
