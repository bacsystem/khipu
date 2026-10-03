package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AltaAsistidaUseCase.AltaCreada;
import pe.factura.application.port.in.AltaAsistidaUseCase.Solicitud;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.service.AltaAsistidaService;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La garantía de #188: «si algo falla a mitad, no queda una cuenta sin empresa ni una empresa huérfana». Con fakes en memoria no hay
 * rollback que probar: hace falta Postgres. El fallo se inyecta con un trigger en cada tabla que el alta escribe, una por una, para
 * que cada paso tenga su turno de ser el que falla con todo lo anterior ya escrito.
 */
class AltaAsistidaTransaccionalTest extends PersistenciaTestBase {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T15:00:00Z"), ZoneId.of("America/Lima"));
    static final List<String> TABLAS_DEL_ALTA = List.of("cuenta", "usuario", "tenant", "api_key", "serie", "token_recuperacion", "auditoria_admin");
    static final Solicitud SOLICITUD = new Solicitud("Comercial Andina", "ana@andina.pe", "987654321", "20100066603", "COMERCIAL ANDINA SAC", null, TipoDocumento.FACTURA, "F001");

    SecretCipher sinCifrar = new SecretCipher() { public byte[] cifrar(byte[] p) { return p; } public byte[] descifrar(byte[] c) { return c; } };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    List<String> correos = new ArrayList<>();
    CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) { correos.add(para); }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
    };

    AltaAsistidaService servicio() {
        return new AltaAsistidaService(new JdbcCuentaRepository(jdbc), new JdbcUsuarioRepository(jdbc), new JdbcSesionRepository(jdbc),
                new JdbcTenantRepository(jdbc, sinCifrar), new JdbcSerieRepository(jdbc), new JdbcApiKeyRepository(jdbc), hasher, correo, uow,
                new JdbcAuditoriaAdminRepository(jdbc), "pepper", CLOCK);
    }

    long filas(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    void fallarAlInsertarEn(String tabla) {
        jdbc.execute("CREATE OR REPLACE FUNCTION falla_inyectada() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'falla inyectada'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER falla_inyectada BEFORE INSERT ON " + tabla + " FOR EACH ROW EXECUTE FUNCTION falla_inyectada()");
    }

    void repararLaTabla(String tabla) { jdbc.execute("DROP TRIGGER falla_inyectada ON " + tabla); }

    @Test void conTodoSanoQuedanLaCuentaLaEmpresaSuSerieSuApiKeyLaInvitacionYLaBitacoraJuntas() {
        AltaCreada r = servicio().alta(ACTOR, SOLICITUD, "https://portal.khipu.test");

        for (String tabla : TABLAS_DEL_ALTA) assertThat(filas(tabla)).as(tabla).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cuenta_id FROM tenant WHERE id = ?", UUID.class, r.tenant().id())).isEqualTo(r.cuentaId());
        assertThat(jdbc.queryForObject("SELECT cuenta_id FROM usuario WHERE email = 'ana@andina.pe'", UUID.class)).isEqualTo(r.cuentaId());
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM serie WHERE codigo = 'F001'", UUID.class)).isEqualTo(r.tenant().id());
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM api_key", UUID.class)).isEqualTo(r.tenant().id());
        var bitacora = jdbc.queryForMap("SELECT * FROM auditoria_admin");
        assertThat(bitacora.get("accion")).isEqualTo("CREAR_CUENTA");
        assertThat(bitacora.get("cuenta_id")).isEqualTo(r.cuentaId());
        assertThat(bitacora.get("tenant_id")).isEqualTo(r.tenant().id());
        assertThat(correos).containsExactly("ana@andina.pe");
    }

    @Test void siFallaCualquierPasoNoQuedaNadaDeLosAnteriores() {
        for (String tabla : TABLAS_DEL_ALTA) {
            fallarAlInsertarEn(tabla);
            try {
                assertThatThrownBy(() -> servicio().alta(ACTOR, SOLICITUD, "https://portal.khipu.test")).as("falla en " + tabla).isNotNull();
            } finally {
                repararLaTabla(tabla);
            }
            for (String t : TABLAS_DEL_ALTA) assertThat(filas(t)).as("tras fallar en " + tabla + ", la tabla " + t).isZero();
        }
        assertThat(correos).as("sin alta no hay invitación").isEmpty();
    }

    @Test void despuesDeUnFalloElMismoAltaSePuedeReintentar() {
        fallarAlInsertarEn("serie");
        assertThatThrownBy(() -> servicio().alta(ACTOR, SOLICITUD, "https://portal.khipu.test")).isNotNull();
        repararLaTabla("serie");

        // El correo y el RUC no quedaron «tomados» por el intento fallido.
        AltaCreada r = servicio().alta(ACTOR, SOLICITUD, "https://portal.khipu.test");
        assertThat(filas("cuenta")).isEqualTo(1);
        assertThat(r.apiKeyEnClaro()).startsWith("fk_");
    }
}
