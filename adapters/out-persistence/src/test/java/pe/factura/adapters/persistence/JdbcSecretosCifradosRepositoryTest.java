package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SecretCipher;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** S2: rotar la MASTER_KEY vuelve a cifrar con la vigente todo lo que la plataforma guarda cifrado. */
class JdbcSecretosCifradosRepositoryTest extends PersistenciaTestBase {
    /** Cifrador de prueba: antepone la «versión» de la clave (1 = anterior, 2 = vigente). El real es AES-GCM, que verifica con qué clave se cifró. */
    static final class Versionado implements SecretCipher {
        final byte vigente; final boolean rotando;
        Versionado(int vigente, boolean rotando) { this.vigente = (byte) vigente; this.rotando = rotando; }
        public byte[] cifrar(byte[] p) { byte[] o = new byte[p.length + 1]; o[0] = vigente; System.arraycopy(p, 0, o, 1, p.length); return o; }
        public byte[] descifrar(byte[] c) {
            if (c[0] != vigente && !(rotando && c[0] == vigente - 1)) throw new IllegalStateException("clave incorrecta");
            return Arrays.copyOfRange(c, 1, c.length);
        }
        public boolean necesitaRecifrar(byte[] c) { descifrar(c); return c[0] != vigente; }
        public boolean rotando() { return rotando; }
    }

    final Versionado vieja = new Versionado(1, false);
    final Versionado rotando = new Versionado(2, true);

    /** La base de los tests no vacía `idempotencia`: sin esto, lo que deja un test cuenta en el siguiente. */
    @org.junit.jupiter.api.BeforeEach void sinRespuestasGuardadas() { jdbc.update("TRUNCATE idempotencia"); }

    static byte[] texto(String s) { return s.getBytes(java.nio.charset.StandardCharsets.UTF_8); }

    UUID tenantConSecretos(SecretCipher c) {
        UUID t = tenantDePrueba();
        jdbc.update("UPDATE tenant SET sol_usuario_enc = ?, sol_clave_enc = ?, cert_pkcs12_enc = ?, cert_clave_enc = ? WHERE id = ?",
                c.cifrar(texto("MODDATOS")), c.cifrar(texto("moddatos")), c.cifrar(new byte[]{9, 9, 9}), c.cifrar(texto("clave-p12")), t);
        return t;
    }

    UUID administradorConSegundoFactor(SecretCipher c) {
        UUID a = UUID.randomUUID();
        jdbc.update("INSERT INTO administrador (id, email, password_hash, activo) VALUES (?, ?, 'h', true)", a, a + "@khipu.pe");
        jdbc.update("INSERT INTO administrador_segundo_factor (administrador_id, secreto_cifrado) VALUES (?, ?)", a, c.cifrar(texto("TOTP-SECRETO")));
        return a;
    }

    void respuestaGuardada(SecretCipher c, String clave) {
        jdbc.update("INSERT INTO idempotencia (alcance, clave, huella, respuesta_cifrada) VALUES ('alta', ?, ?, ?)", clave, "0".repeat(64), c.cifrar(texto("fk_secreto")));
    }

    @Test void recifraTodasLasColumnasYLasDejaLegiblesConLaClaveNueva() {
        UUID t = tenantConSecretos(vieja);
        UUID a = administradorConSegundoFactor(vieja);
        respuestaGuardada(vieja, "k1");
        var repo = new JdbcSecretosCifradosRepository(jdbc, rotando);

        assertThat(repo.pendientes()).isEqualTo(6);
        assertThat(repo.recifrar()).isEqualTo(6);
        assertThat(repo.pendientes()).isZero();

        // Sin la anterior, la clave nueva sola ya lee todo.
        Versionado soloNueva = new Versionado(2, false);
        assertThat(soloNueva.descifrar(jdbc.queryForObject("SELECT sol_clave_enc FROM tenant WHERE id = ?", byte[].class, t))).isEqualTo(texto("moddatos"));
        assertThat(soloNueva.descifrar(jdbc.queryForObject("SELECT cert_pkcs12_enc FROM tenant WHERE id = ?", byte[].class, t))).containsExactly(9, 9, 9);
        assertThat(soloNueva.descifrar(jdbc.queryForObject("SELECT secreto_cifrado FROM administrador_segundo_factor WHERE administrador_id = ?", byte[].class, a)))
                .isEqualTo(texto("TOTP-SECRETO"));
        assertThat(soloNueva.descifrar(jdbc.queryForObject("SELECT respuesta_cifrada FROM idempotencia WHERE clave = 'k1'", byte[].class)))
                .isEqualTo(texto("fk_secreto"));
    }

    @Test void esIdempotenteYNoTocaLoQueYaEstaConLaVigente() {
        tenantConSecretos(vieja);
        tenantConSecretos(rotando);
        var repo = new JdbcSecretosCifradosRepository(jdbc, rotando);

        assertThat(repo.recifrar()).as("solo los 4 de la empresa vieja").isEqualTo(4);
        assertThat(repo.recifrar()).isZero();
    }

    @Test void lasColumnasVaciasNoCuentan() {
        tenantDePrueba();
        assertThat(new JdbcSecretosCifradosRepository(jdbc, rotando).pendientes()).isZero();
    }

    /**
     * Toda columna binaria de la base es un secreto cifrado con la MASTER_KEY, y tiene que estar en la lista del recifrado: una que falte quedaría
     * ilegible al quitar MASTER_KEY_ANTERIOR. Si una columna binaria nueva no es un secreto, se la exceptúa aquí con su motivo.
     */
    @Test void ningunaColumnaBinariaQuedaFueraDelRecifrado() {
        var binarias = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND data_type = 'bytea' AND table_name <> 'flyway_schema_history'
                """, String.class);
        var enLaLista = JdbcSecretosCifradosRepository.COLUMNAS.stream().map(c -> c.tabla() + "." + c.columna()).toList();
        assertThat(enLaLista).containsExactlyInAnyOrderElementsOf(binarias);
    }

    @Test void sinRotacionNoHayNadaPendiente() {
        tenantConSecretos(vieja);
        var repo = new JdbcSecretosCifradosRepository(jdbc, vieja);
        assertThat(repo.pendientes()).isZero();
        assertThat(repo.recifrar()).isZero();
    }
}
