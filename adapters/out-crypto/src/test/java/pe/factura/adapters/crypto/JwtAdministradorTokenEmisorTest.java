package pe.factura.adapters.crypto;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AdministradorTokenEmisor;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.domain.cuenta.Rol;

import java.time.Duration;
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

    // --- #177: sesión configurable y token de desafío ------------------------------------------------------------------------

    @Test void laVidaDeLaSesionEsLaConfigurada() {
        JwtAdministradorTokenEmisor de15 = new JwtAdministradorTokenEmisor(secreto, Duration.ofMinutes(15));
        Instant antes = Instant.now();
        var jwt = JWT.decode(de15.emitir(new AdministradorTokenEmisor.Claims(UUID.randomUUID(), "a@b.pe")));
        assertThat(jwt.getExpiresAtAsInstant()).isBetween(antes.plusSeconds(15 * 60 - 1), Instant.now().plusSeconds(15 * 60 + 1));
        assertThat(de15.vidaSesionSegundos()).isEqualTo(15 * 60);
        assertThat(emisor.vidaSesionSegundos()).as("30 minutos por defecto").isEqualTo(30 * 60);
    }

    @Test void elDesafioVerificaComoDesafioYVenceEnCincoMinutos() {
        UUID id = UUID.randomUUID();
        Instant antes = Instant.now();
        String desafio = emisor.emitirDesafio(id);
        assertThat(emisor.verificarDesafio(desafio)).hasValue(id);
        assertThat(JWT.decode(desafio).getExpiresAtAsInstant()).isBetween(antes.plusSeconds(5 * 60 - 1), Instant.now().plusSeconds(5 * 60 + 1));
    }

    /** El corazón de #177: con la contraseña sola se obtiene un desafío, y con él no se opera el backoffice. */
    @Test void unDesafioNoSirveComoSesionNiUnaSesionComoDesafio() {
        UUID id = UUID.randomUUID();
        assertThat(emisor.verificar(emisor.emitirDesafio(id))).isEmpty();
        assertThat(emisor.verificarDesafio(emisor.emitir(new AdministradorTokenEmisor.Claims(id, "a@b.pe")))).isEmpty();
        JwtTokenEmisor emisorCliente = new JwtTokenEmisor(secreto);
        assertThat(emisor.verificarDesafio(emisorCliente.emitir(new TokenEmisor.Claims(id, UUID.randomUUID(), Rol.ADMIN)))).isEmpty();
    }

    @Test void unDesafioVencidoOAjenoNoVerifica() {
        String vencido = JWT.create().withSubject(UUID.randomUUID().toString()).withClaim("tipo", "plataforma-desafio")
                .withExpiresAt(Date.from(Instant.now().minusSeconds(1))).sign(Algorithm.HMAC256(Base64.getDecoder().decode(secreto)));
        assertThat(emisor.verificarDesafio(vencido)).isEmpty();
        JwtAdministradorTokenEmisor otro = new JwtAdministradorTokenEmisor(Base64.getEncoder().encodeToString("otra-clave-de-32-bytes-diferente".getBytes()));
        assertThat(emisor.verificarDesafio(otro.emitirDesafio(UUID.randomUUID()))).isEmpty();
        assertThat(emisor.verificarDesafio(null)).isEmpty();
        assertThat(emisor.verificarDesafio("basura")).isEmpty();
    }
}
