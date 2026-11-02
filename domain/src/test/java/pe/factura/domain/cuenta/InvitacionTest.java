package pe.factura.domain.cuenta;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #21: una invitación sirve una vez, mientras esté pendiente y sin vencer. */
class InvitacionTest {
    final Instant ahora = Instant.parse("2026-10-20T12:00:00Z");
    final Duration vida = Duration.ofDays(7);

    private Invitacion nueva() {
        return Invitacion.nueva(UUID.randomUUID(), " Beto@B.pe ", Rol.EMISOR, "h1", ahora, vida, UUID.randomUUID());
    }

    @Test void nacePendienteConElCorreoNormalizadoYUnEnvio() {
        Invitacion i = nueva();
        assertThat(i.email()).isEqualTo("beto@b.pe");
        assertThat(i.estado()).isEqualTo(Invitacion.Estado.PENDIENTE);
        assertThat(i.envios()).isEqualTo(1);
        assertThat(i.expiraEn()).isEqualTo(ahora.plus(vida));
        assertThat(i.aceptable(ahora)).isTrue();
    }

    @Test void vencidaNoSeAcepta() {
        Invitacion i = nueva();
        assertThat(i.aceptable(ahora.plus(vida).minusSeconds(1))).isTrue();
        assertThat(i.aceptable(ahora.plus(vida))).isFalse();
        assertThat(i.vencida(ahora.plus(vida))).isTrue();
    }

    @Test void revocadaNoSeAceptaNiSeReenvia() {
        Invitacion r = nueva().revocada();
        assertThat(r.aceptable(ahora)).isFalse();
        assertThatThrownBy(() -> r.reenviada("h2", ahora, vida)).extracting("codigo").isEqualTo("INVITACION_NO_PENDIENTE");
        assertThatThrownBy(r::revocada).extracting("codigo").isEqualTo("INVITACION_NO_PENDIENTE");
    }

    @Test void reenviarCambiaElTokenYVuelveAContarLaVida() {
        Invitacion i = nueva();
        Instant despues = ahora.plus(Duration.ofDays(10));   // ya vencida: reenviarla es lo que la revive
        Invitacion r = i.reenviada("h2", despues, vida);
        assertThat(r.tokenHash()).isEqualTo("h2");
        assertThat(r.expiraEn()).isEqualTo(despues.plus(vida));
        assertThat(r.envios()).isEqualTo(2);
        assertThat(r.aceptable(despues)).isTrue();
    }

    @Test void elReenvioTieneTope() {
        Invitacion i = nueva();
        for (int n = 1; n < Invitacion.MAX_ENVIOS; n++) i = i.reenviada("h" + n, ahora, vida);
        Invitacion tope = i;
        assertThat(tope.envios()).isEqualTo(Invitacion.MAX_ENVIOS);
        assertThatThrownBy(() -> tope.reenviada("otro", ahora, vida)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DEMASIADOS_ENVIOS");
    }

    @Test void exigeUnCorreoValidoYUnRol() {
        assertThatThrownBy(() -> Invitacion.nueva(UUID.randomUUID(), "no-es-correo", Rol.EMISOR, "h", ahora, vida, null)).extracting("codigo").isEqualTo("EMAIL_INVALIDO");
        assertThatThrownBy(() -> Invitacion.nueva(UUID.randomUUID(), "a@b.pe", null, "h", ahora, vida, null)).extracting("codigo").isEqualTo("ROL_REQUERIDO");
    }
}
