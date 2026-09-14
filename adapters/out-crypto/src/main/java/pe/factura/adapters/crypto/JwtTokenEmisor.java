package pe.factura.adapters.crypto;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.domain.cuenta.Rol;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** JWT HMAC-SHA256 de acceso, corta vida (15 min por defecto). El refresh es un token opaco (ver TokenOpaco en application). */
public class JwtTokenEmisor implements TokenEmisor {
    private static final Duration VIDA_ACCESS = Duration.ofMinutes(15);
    private static final String CLAIM_CUENTA = "cuenta";
    private static final String CLAIM_ROL = "rol";

    private final Algorithm algoritmo;

    public JwtTokenEmisor(String secretoBase64) {
        if (secretoBase64 == null || secretoBase64.isBlank())
            throw new IllegalArgumentException("JWT_SECRET es obligatorio");
        byte[] secreto = java.util.Base64.getDecoder().decode(secretoBase64);
        if (secreto.length < 32)
            throw new IllegalArgumentException("JWT_SECRET debe decodificar a al menos 32 bytes");
        this.algoritmo = Algorithm.HMAC256(secreto);
    }

    @Override public String emitir(Claims claims) {
        Instant ahora = Instant.now();
        return JWT.create()
                .withSubject(claims.usuarioId().toString())
                .withClaim(CLAIM_CUENTA, claims.cuentaId().toString())
                .withClaim(CLAIM_ROL, claims.rol().name())
                .withIssuedAt(Date.from(ahora))
                .withExpiresAt(Date.from(ahora.plus(VIDA_ACCESS)))
                .sign(algoritmo);
    }

    @Override public Optional<Claims> verificar(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            DecodedJWT jwt = JWT.require(algoritmo).build().verify(token);
            UUID usuarioId = UUID.fromString(jwt.getSubject());
            UUID cuentaId = UUID.fromString(jwt.getClaim(CLAIM_CUENTA).asString());
            Rol rol = Rol.valueOf(jwt.getClaim(CLAIM_ROL).asString());
            return Optional.of(new Claims(usuarioId, cuentaId, rol));
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
