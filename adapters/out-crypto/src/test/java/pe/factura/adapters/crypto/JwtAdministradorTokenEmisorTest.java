package pe.factura.adapters.crypto;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AdministradorTokenEmisor;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.domain.cuenta.Rol;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtAdministradorTokenEmisorTest {
    String secreto = Base64.getEncoder().encodeToString(new byte[32]);
    JwtAdministradorTokenEmisor emisor = new JwtAdministradorTokenEmisor(secreto);

    @Test void emiteYVerifica() {
        UUID id = UUID.randomUUID();
        String token = emisor.emitir(new AdministradorTokenEmisor.Claims(id, "ana@khipu.pe"));
        AdministradorTokenEmisor.Claims c = emisor.verificar(token).orElseThrow();
        assertThat(c.administradorId()).isEqualTo(id);
        assertThat(c.email()).isEqualTo("ana@khipu.pe");
    }

    @Test void tokenInvalidoOVacioNoVerifica() {
        assertThat(emisor.verificar("basura")).isEmpty();
        assertThat(emisor.verificar(null)).isEmpty();
        assertThat(emisor.verificar("")).isEmpty();
    }

    @Test void tokenFirmadoConOtraClaveNoVerifica() {
        String token = emisor.emitir(new AdministradorTokenEmisor.Claims(UUID.randomUUID(), "a@b.pe"));
        JwtAdministradorTokenEmisor otro = new JwtAdministradorTokenEmisor(Base64.getEncoder().encodeToString("otra-clave-de-32-bytes-diferente".getBytes()));
        assertThat(otro.verificar(token)).isEmpty();
    }

    /**
     * La propiedad que justifica todo #175: aunque ambos tokens usen el mismo JWT_SECRET, un JWT de
     * cliente jamás debe pasar como uno de administrador, ni al revés — el claim "tipo" los separa.
     */
    @Test void unJwtDeClienteNuncaVerificaComoAdministrador() {
        JwtTokenEmisor emisorCliente = new JwtTokenEmisor(secreto);
        String tokenCliente = emisorCliente.emitir(new TokenEmisor.Claims(UUID.randomUUID(), UUID.randomUUID(), Rol.ADMIN));
        assertThat(emisor.verificar(tokenCliente)).isEmpty();
    }

    @Test void unJwtDeAdministradorNuncaVerificaComoCliente() {
        JwtTokenEmisor emisorCliente = new JwtTokenEmisor(secreto);
        String tokenAdmin = emisor.emitir(new AdministradorTokenEmisor.Claims(UUID.randomUUID(), "a@b.pe"));
        assertThat(emisorCliente.verificar(tokenAdmin)).isEmpty();
    }

    /**
     * Aísla el claim "tipo" del chequeo de "email": un token firmado con la misma clave, con "email" pero sin
     * tipo=plataforma, no debe verificar. Sin este test, quitar `.withClaim(CLAIM_TIPO, ...)` sobrevive: el chequeo
     * de "email" nulo ya rechaza un JWT de cliente (que no trae "email"), pero por una razón distinta.
     */
    @Test void unTokenConEmailPeroSinTipoPlataformaNoVerifica() {
        String token = JWT.create()
                .withSubject(UUID.randomUUID().toString())
                .withClaim("email", "a@b.pe")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(60)))
                .sign(Algorithm.HMAC256(Base64.getDecoder().decode(secreto)));
        assertThat(emisor.verificar(token)).isEmpty();
    }

    @Test void secretoInvalidoFallaAlConstruir() {
        assertThatThrownBy(() -> new JwtAdministradorTokenEmisor(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtAdministradorTokenEmisor("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtAdministradorTokenEmisor(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
