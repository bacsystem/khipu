package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AccesosDeSoporteRepository.Registro;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Los accesos de soporte (#184) que ve el cliente, leídos de la bitácora con Postgres real. Lo que importa: solo los de su cuenta y solo los de impersonación. */
class JdbcAccesosDeSoporteRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    JdbcAccesosDeSoporteRepository repo = new JdbcAccesosDeSoporteRepository(jdbc);

    void registrar(UUID cuenta, String accion, String detalle, Instant cuando) {
        jdbc.update("INSERT INTO auditoria_admin (id, actor_tipo, accion, cuenta_id, detalle, ip, ocurrido_en) VALUES (?, 'CLAVE_PLATAFORMA', ?, ?, ?, '127.0.0.1', ?)",
                UUID.randomUUID(), accion, cuenta, detalle, Timestamp.from(cuando));
    }

    @Test void devuelveLasImpersonacionesDeLaMasRecienteALaMasAntigua() {
        UUID c = UUID.randomUUID();
        registrar(c, "IMPERSONAR_USUARIO", "usuario=a@x.pe duracion_s=900", T0);
        registrar(c, "IMPERSONAR_USUARIO", "usuario=b@x.pe duracion_s=900", T0.plusSeconds(3600));

        List<Registro> r = repo.deLaCuenta(c, 10);

        assertThat(r).extracting(Registro::detalle).containsExactly("usuario=b@x.pe duracion_s=900", "usuario=a@x.pe duracion_s=900");
        assertThat(r.get(0).ocurridoEn()).isEqualTo(T0.plusSeconds(3600));
    }

    /** Lo que el cliente ve es solo lo de su cuenta: nunca los accesos a otra. */
    @Test void soloDevuelveLosDeLaCuentaPedida() {
        UUID mia = UUID.randomUUID(), otra = UUID.randomUUID();
        registrar(mia, "IMPERSONAR_USUARIO", "usuario=a@x.pe duracion_s=900", T0);
        registrar(otra, "IMPERSONAR_USUARIO", "usuario=z@otra.pe duracion_s=900", T0.plusSeconds(10));

        assertThat(repo.deLaCuenta(mia, 10)).extracting(Registro::detalle).containsExactly("usuario=a@x.pe duracion_s=900");
        assertThat(repo.deLaCuenta(UUID.randomUUID(), 10)).isEmpty();
    }

    /** Las demás acciones del administrador sobre la cuenta (suspender, dar de baja…) no son accesos de soporte y no se le muestran como tales. */
    @Test void ignoraLasDemasAccionesDeLaBitacora() {
        UUID c = UUID.randomUUID();
        registrar(c, "SUSPENDER_CUENTA", "motivo=no pagó", T0);
        registrar(c, "ENVIAR_RESTABLECIMIENTO", "usuario=a@x.pe", T0.plusSeconds(1));
        registrar(c, "IMPERSONAR_USUARIO", "usuario=a@x.pe duracion_s=900", T0.plusSeconds(2));

        assertThat(repo.deLaCuenta(c, 10)).hasSize(1);
    }

    @Test void respetaElLimiteYQuedaConLosMasRecientes() {
        UUID c = UUID.randomUUID();
        for (int i = 0; i < 5; i++) registrar(c, "IMPERSONAR_USUARIO", "usuario=u" + i + "@x.pe duracion_s=900", T0.plusSeconds(i * 60L));

        assertThat(repo.deLaCuenta(c, 2)).extracting(Registro::detalle).containsExactly("usuario=u4@x.pe duracion_s=900", "usuario=u3@x.pe duracion_s=900");
    }

    @Test void unRegistroSinDetalleSeDevuelveIgual() {
        UUID c = UUID.randomUUID();
        registrar(c, "IMPERSONAR_USUARIO", null, T0);

        assertThat(repo.deLaCuenta(c, 10)).containsExactly(new Registro(T0, null));
    }

    /** Con poco volumen Postgres prefiere recorrer la tabla: se le prohíbe, porque lo que se comprueba es que el índice sirve para esta consulta, no que gane con pocas filas. */
    @Test void laLecturaSeApoyaEnElIndiceDeLaCuenta() {
        String plan = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) con -> {
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("SET enable_seqscan = off");
                StringBuilder texto = new StringBuilder();
                try (java.sql.ResultSet rs = st.executeQuery("EXPLAIN SELECT ocurrido_en, detalle FROM auditoria_admin WHERE cuenta_id = '" + UUID.randomUUID()
                        + "' AND accion = 'IMPERSONAR_USUARIO' ORDER BY ocurrido_en DESC, id LIMIT 100")) {
                    while (rs.next()) texto.append(rs.getString(1)).append(System.lineSeparator());
                }
                return texto.toString();
            }
        });

        assertThat(plan).contains("ix_auditoria_cuenta");
    }
}
