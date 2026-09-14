package pe.factura.adapters.crypto;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.domain.cuenta.Rol;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtTokenEmisorTest {
    String secreto = Base64.getEncoder().encodeToString(new byte[32]);
    JwtTokenEmisor emisor = new JwtTokenEmisor(secreto);

    @Test void emiteYVerifica() {
        UUID usuario = UUID.randomUUID(), cuenta = UUID.randomUUID();
        String token = emisor.emitir(new TokenEmisor.Claims(usuario, cuenta, Rol.ADMIN));
        TokenEmisor.Claims c = emisor.verificar(token).orElseThrow();
        assertThat(c.usuarioId()).isEqualTo(usuario);
        assertThat(c.cuentaId()).isEqualTo(cuenta);
        assertThat(c.rol()).isEqualTo(Rol.ADMIN);
    }

    @Test void tokenInvalidoOVacioNoVerifica() {
        assertThat(emisor.verificar("basura")).isEmpty();
        assertThat(emisor.verificar(null)).isEmpty();
        assertThat(emisor.verificar("")).isEmpty();
    }

    @Test void tokenFirmadoConOtraClaveNoVerifica() {
        String token = emisor.emitir(new TokenEmisor.Claims(UUID.randomUUID(), UUID.randomUUID(), Rol.EMISOR));
        JwtTokenEmisor otro = new JwtTokenEmisor(Base64.getEncoder().encodeToString("otra-clave-de-32-bytes-diferente".getBytes()));
        assertThat(otro.verificar(token)).isEmpty();
    }

    @Test void secretoInvalidoFallaAlConstruir() {
        assertThatThrownBy(() -> new JwtTokenEmisor(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenEmisor("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenEmisor(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
