package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorAdminTest {
    @Test void administradorConservaSuIdYLaIp() {
        UUID id = UUID.randomUUID();
        ActorAdmin actor = ActorAdmin.administrador(id, "203.0.113.7");
        assertThat(actor.tipo()).isEqualTo(ActorAdmin.Tipo.ADMINISTRADOR);
        assertThat(actor.administradorId()).isEqualTo(id);
        assertThat(actor.ip()).isEqualTo("203.0.113.7");
    }

    @Test void laClaveDePlataformaNoTieneAdministrador() {
        ActorAdmin actor = ActorAdmin.clavePlataforma("203.0.113.7");
        assertThat(actor.tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
        assertThat(actor.administradorId()).isNull();
    }

    @Test void laClaveDePlataformaNoPuedeAtribuirseAUnAdministrador() {
        // La fábrica no lo permite, pero el constructor del record es público: la invariante debe vivir en el record.
        assertThatThrownBy(() -> new ActorAdmin(ActorAdmin.Tipo.CLAVE_PLATAFORMA, UUID.randomUUID(), "203.0.113.7"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
    }

    @Test void unAdministradorSinIdNoEsUnActorValido() {
        assertThatThrownBy(() -> ActorAdmin.administrador(null, "203.0.113.7"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
    }

    @Test void sinIpNoHayAuditoriaDefendible() {
        assertThatThrownBy(() -> ActorAdmin.clavePlataforma(" "))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
        assertThatThrownBy(() -> ActorAdmin.administrador(UUID.randomUUID(), null))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
    }
}
