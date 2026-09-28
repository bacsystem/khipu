package pe.factura.adapters.crypto;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import pe.factura.application.port.out.AdministradorTokenEmisor;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT HMAC-SHA256 del administrador de la plataforma. Reusa el mismo JWT_SECRET que
 * {@link JwtTokenEmisor} (un secreto más no compensa el riesgo operativo de gestionar dos), pero la
 * verificación exige el claim {@code tipo=plataforma}: un JWT de cliente no lo trae (le faltaría
 * también, pero este chequeo lo hace explícito e intencional), así que nunca pasa por uno de
 * administrador. Sin refresh: sesión corta (30 min) y a volver a iniciar sesión — #177 endurece esto
 * con 2FA y define la política de sesión definitiva.
 */
public class JwtAdministradorTokenEmisor implements AdministradorTokenEmisor {
    private static final Duration VIDA_ACCESS = Duration.ofMinutes(30);
    private static final String CLAIM_TIPO = "tipo";
    private static final String TIPO_PLATAFORMA = "plataforma";
    private static final String CLAIM_EMAIL = "email";

    private final Algorithm algoritmo;

    public JwtAdministradorTokenEmisor(String secretoBase64) {
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
                .withSubject(claims.administradorId().toString())
                .withClaim(CLAIM_TIPO, TIPO_PLATAFORMA)
                .withClaim(CLAIM_EMAIL, claims.email())
                .withIssuedAt(Date.from(ahora))
                .withExpiresAt(Date.from(ahora.plus(VIDA_ACCESS)))
                .sign(algoritmo);
    }

    @Override public Optional<Claims> verificar(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            DecodedJWT jwt = JWT.require(algoritmo).withClaim(CLAIM_TIPO, TIPO_PLATAFORMA).build().verify(token);
            UUID administradorId = UUID.fromString(jwt.getSubject());
            String email = jwt.getClaim(CLAIM_EMAIL).asString();
            if (email == null || email.isBlank()) return Optional.empty();
            return Optional.of(new Claims(administradorId, email));
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
