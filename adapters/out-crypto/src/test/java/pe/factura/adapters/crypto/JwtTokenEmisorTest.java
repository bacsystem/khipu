package pe.factura.adapters.crypto;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.domain.cuenta.Rol;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
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

    // --- sesión de soporte (#184) ------------------------------------------------------------------------------------------------------

    @Test void unaSesionNormalNoEsDeSoporte() {
        String token = emisor.emitir(new TokenEmisor.Claims(UUID.randomUUID(), UUID.randomUUID(), Rol.ADMIN));

        TokenEmisor.Claims c = emisor.verificar(token).orElseThrow();

        assertThat(c.esSoporte()).isFalse();
        assertThat(c.soporte()).isNull();
    }

    @Test void unaSesionDeSoporteLlevaAlAdministradorYSuExpiracion() {
        UUID usuario = UUID.randomUUID(), cuenta = UUID.randomUUID(), admin = UUID.randomUUID();
        Instant expira = Instant.now().plus(Duration.ofMinutes(10));

        TokenEmisor.Claims c = emisor.verificar(emisor.emitir(new TokenEmisor.Claims(usuario, cuenta, Rol.ADMIN, new TokenEmisor.Soporte(admin, expira)))).orElseThrow();

        assertThat(c.esSoporte()).isTrue();
        assertThat(c.usuarioId()).as("el token es del usuario al que se mira").isEqualTo(usuario);
        assertThat(c.cuentaId()).isEqualTo(cuenta);
        assertThat(c.soporte().administradorId()).isEqualTo(admin);
        assertThat(c.soporte().expiraEn().getEpochSecond()).isEqualTo(expira.getEpochSecond());
    }

    /** La expiración de una sesión de soporte es la suya, no los 15 minutos de una normal: corta y sin posibilidad de alargarla. */
    @Test void laSesionDeSoporteVenceCuandoDiceYNoAntesNiDespues() {
        TokenEmisor.Claims normal = new TokenEmisor.Claims(UUID.randomUUID(), UUID.randomUUID(), Rol.ADMIN);
        TokenEmisor.Soporte corta = new TokenEmisor.Soporte(UUID.randomUUID(), Instant.now().plus(Duration.ofMinutes(2)));

        long exp = com.auth0.jwt.JWT.decode(emisor.emitir(new TokenEmisor.Claims(normal.usuarioId(), normal.cuentaId(), normal.rol(), corta))).getExpiresAt().getTime() / 1000;

        assertThat(exp).isEqualTo(corta.expiraEn().getEpochSecond());
        long normalExp = com.auth0.jwt.JWT.decode(emisor.emitir(normal)).getExpiresAt().getTime() / 1000;
        assertThat(normalExp - Instant.now().getEpochSecond()).as("una sesión normal sigue durando 15 minutos").isBetween(14L * 60, 15L * 60 + 5);
    }

    @Test void unaSesionDeSoporteYaVencidaNoVerifica() {
        String token = emisor.emitir(new TokenEmisor.Claims(UUID.randomUUID(), UUID.randomUUID(), Rol.ADMIN,
                new TokenEmisor.Soporte(UUID.randomUUID(), Instant.now().minusSeconds(30))));

        assertThat(emisor.verificar(token)).isEmpty();
    }

    /** Un claim de soporte que no se entiende nunca se degrada a una sesión normal: el token se rechaza. */
    @Test void unClaimDeSoporteMalformadoInvalidaElToken() {
        String token = com.auth0.jwt.JWT.create()
                .withSubject(UUID.randomUUID().toString()).withClaim("cuenta", UUID.randomUUID().toString()).withClaim("rol", "ADMIN")
                .withClaim("imp", "no-es-un-uuid")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(300)))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(new byte[32]));

        assertThat(emisor.verificar(token)).isEmpty();
    }

    /** El claim lo protege la firma: quitarlo para volver el token una sesión normal lo invalida. */
    @Test void quitarElClaimDeSoporteInvalidaLaFirma() {
        String token = emisor.emitir(new TokenEmisor.Claims(UUID.randomUUID(), UUID.randomUUID(), Rol.ADMIN, new TokenEmisor.Soporte(UUID.randomUUID(), Instant.now().plusSeconds(300))));
        String[] partes = token.split("\\.");
        String cuerpo = new String(Base64.getUrlDecoder().decode(partes[1]));
        String sinImp = cuerpo.replaceAll(",\"imp\":\"[^\"]*\"", "").replaceAll("\"imp\":\"[^\"]*\",", "");
        String manipulado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sinImp.getBytes()) + "." + partes[2];

        assertThat(sinImp).as("el claim se quitó de verdad").isNotEqualTo(cuerpo);
        assertThat(emisor.verificar(manipulado)).isEmpty();
    }
}
