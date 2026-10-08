package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.BannerRepository;
import pe.factura.application.port.out.PlantillasRepository;
import pe.factura.application.port.out.RemitenteRepository;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La configuración de la plataforma que un administrador cambia sin un despliegue (#199): tres tablas chicas, una por cosa. El remitente y el aviso son una fila única (`id = 1`) y
 * se reemplazan con un `INSERT … ON CONFLICT`; los correos son una fila por tipo. Una plantilla guardada con un tipo que esta versión ya no conoce se ignora en vez de romper la
 * lectura de las demás.
 */
public final class JdbcConfiguracionDePlataforma {
    private JdbcConfiguracionDePlataforma() {}

    @RequiredArgsConstructor
    public static class Remitente implements RemitenteRepository {
        private final JdbcTemplate jdbc;

        @Override public Optional<Guardado> buscar() {
            // El correo de quien lo fijó (H11) sale de un LEFT JOIN: sin FK al administrador, como la bitácora.
            return jdbc.query("""
                            SELECT r.nombre, r.email, r.responder_a, r.actualizado_en, a.email AS actualizado_por
                            FROM remitente_correo r LEFT JOIN administrador a ON a.id = r.actualizado_por WHERE r.id = 1""",
                    (rs, i) -> new Guardado(new RemitenteDeCorreo(rs.getString("nombre"), rs.getString("email"), rs.getString("responder_a")), rs.getTimestamp("actualizado_en").toInstant(),
                            rs.getString("actualizado_por"))).stream().findFirst();
        }

        @Override public void guardar(RemitenteDeCorreo r, Instant ahora, UUID por) {
            jdbc.update("""
                    INSERT INTO remitente_correo (id, nombre, email, responder_a, actualizado_en, actualizado_por) VALUES (1, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, email = EXCLUDED.email, responder_a = EXCLUDED.responder_a,
                                                   actualizado_en = EXCLUDED.actualizado_en, actualizado_por = EXCLUDED.actualizado_por
                    """, r.nombre(), r.email(), r.responderA(), Timestamp.from(ahora), por);
        }

        @Override public boolean quitar() { return jdbc.update("DELETE FROM remitente_correo WHERE id = 1") > 0; }
    }

    @RequiredArgsConstructor
    public static class Plantillas implements PlantillasRepository {
        private final JdbcTemplate jdbc;

        @Override public Optional<Guardada> buscar(PlantillaDeCorreo tipo) {
            return jdbc.query(SELECT_PLANTILLA + " WHERE p.tipo = ?", (rs, i) -> guardada(rs), tipo.name()).stream().findFirst();
        }

        @Override public Map<PlantillaDeCorreo, Guardada> todas() {
            Map<PlantillaDeCorreo, Guardada> r = new EnumMap<>(PlantillaDeCorreo.class);
            for (Fila f : jdbc.query(SELECT_PLANTILLA, (rs, i) -> new Fila(rs.getString("tipo"), guardada(rs))))
                PlantillaDeCorreo.deNombre(f.tipo()).ifPresent(t -> r.put(t, f.guardada()));
            return r;
        }

        private record Fila(String tipo, Guardada guardada) {}

        /** El correo de quien la cambió (H11) sale de un LEFT JOIN: sin FK al administrador, como la bitácora. */
        private static final String SELECT_PLANTILLA = """
                SELECT p.tipo, p.asunto, p.cuerpo, p.actualizado_en, a.email AS actualizado_por
                FROM plantilla_correo p LEFT JOIN administrador a ON a.id = p.actualizado_por""";

        private static Guardada guardada(java.sql.ResultSet rs) throws java.sql.SQLException {
            return new Guardada(new Texto(rs.getString("asunto"), rs.getString("cuerpo")), rs.getTimestamp("actualizado_en").toInstant(), rs.getString("actualizado_por"));
        }

        @Override public void guardar(PlantillaDeCorreo tipo, Texto texto, Instant ahora, UUID por) {
            jdbc.update("""
                    INSERT INTO plantilla_correo (tipo, asunto, cuerpo, actualizado_en, actualizado_por) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (tipo) DO UPDATE SET asunto = EXCLUDED.asunto, cuerpo = EXCLUDED.cuerpo, actualizado_en = EXCLUDED.actualizado_en, actualizado_por = EXCLUDED.actualizado_por
                    """, tipo.name(), texto.asunto(), texto.cuerpo(), Timestamp.from(ahora), por);
        }

        @Override public boolean quitar(PlantillaDeCorreo tipo) { return jdbc.update("DELETE FROM plantilla_correo WHERE tipo = ?", tipo.name()) > 0; }
    }

    @RequiredArgsConstructor
    public static class Banner implements BannerRepository {
        private final JdbcTemplate jdbc;

        @Override public Optional<Guardado> buscar() {
            return jdbc.query("SELECT texto, desde, hasta, actualizado_en FROM banner_mantenimiento WHERE id = 1",
                    (rs, i) -> new Guardado(new BannerDeMantenimiento(rs.getString("texto"), rs.getTimestamp("desde").toInstant(), rs.getTimestamp("hasta").toInstant()),
                            rs.getTimestamp("actualizado_en").toInstant())).stream().findFirst();
        }

        @Override public void guardar(BannerDeMantenimiento b, Instant ahora, UUID por) {
            jdbc.update("""
                    INSERT INTO banner_mantenimiento (id, texto, desde, hasta, actualizado_en, actualizado_por) VALUES (1, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET texto = EXCLUDED.texto, desde = EXCLUDED.desde, hasta = EXCLUDED.hasta,
                                                   actualizado_en = EXCLUDED.actualizado_en, actualizado_por = EXCLUDED.actualizado_por
                    """, b.texto(), Timestamp.from(b.desde()), Timestamp.from(b.hasta()), Timestamp.from(ahora), por);
        }

        @Override public boolean retirar() { return jdbc.update("DELETE FROM banner_mantenimiento WHERE id = 1") > 0; }
    }
}
