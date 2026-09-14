package pe.factura.adapters.crypto;

import org.springframework.security.crypto.bcrypt.BCrypt;
import pe.factura.application.port.out.PasswordHasher;

public class BcryptPasswordHasher implements PasswordHasher {
    private static final int ROUNDS = 12;

    @Override public String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(ROUNDS));
    }

    @Override public boolean coincide(String password, String hash) {
        if (password == null || hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            return false;   // hash malformado: nunca coincide
        }
    }
}
