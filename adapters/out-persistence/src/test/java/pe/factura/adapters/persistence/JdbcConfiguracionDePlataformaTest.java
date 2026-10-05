package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuración de la plataforma (#199) con Postgres real: lo que se guarda vuelve igual, reemplazar no duplica, lo que no se cambió no existe, un cambio dentro de una transacción
 * que falla no queda, y la base rechaza lo que el dominio ya rechazaba (por si algo entrara por otro camino).
 */
class JdbcConfiguracionDePlataformaTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00.123456Z");
    static final UUID ADMIN = UUID.randomUUID();

    JdbcConfiguracionDePlataforma.Remitente remitentes = new JdbcConfiguracionDePlataforma.Remitente(jdbc);
    JdbcConfiguracionDePlataforma.Plantillas plantillas = new JdbcConfiguracionDePlataforma.Plantillas(jdbc);
    JdbcConfiguracionDePlataforma.Banner banners = new JdbcConfiguracionDePlataforma.Banner(jdbc);

    long filas(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    // --- remitente ----------------------------------------------------------------------------------------------------------------------

    @Test void sinRemitenteGuardadoNoHayNada() {
        assertThat(remitentes.buscar()).isEmpty();
        assertThat(remitentes.quitar()).isFalse();
    }

    @Test void elRemitenteVuelveTalComoSeGuardo() {
        remitentes.guardar(new RemitenteDeCorreo("Facturación Perú · khipu", "avisos@khipu.pe", "soporte@khipu.pe"), AHORA, ADMIN);

        var g = remitentes.buscar().orElseThrow();
        assertThat(g.remitente()).isEqualTo(new RemitenteDeCorreo("Facturación Perú · khipu", "avisos@khipu.pe", "soporte@khipu.pe"));
        assertThat(g.actualizadoEn()).isEqualTo(AHORA);
        assertThat(jdbc.queryForObject("SELECT actualizado_por FROM remitente_correo", UUID.class)).isEqualTo(ADMIN);
    }

    @Test void losOpcionalesSeGuardanComoNulosYVuelvenNulos() {
        remitentes.guardar(new RemitenteDeCorreo(null, "avisos@khipu.pe", null), AHORA, null);

        var g = remitentes.buscar().orElseThrow();
        assertThat(g.remitente()).isEqualTo(new RemitenteDeCorreo(null, "avisos@khipu.pe", null));
        assertThat(jdbc.queryForObject("SELECT actualizado_por FROM remitente_correo", UUID.class)).as("la clave de la plataforma no es un administrador").isNull();
    }

    @Test void guardarOtroRemitenteReemplazaAlAnteriorSinDuplicar() {
        remitentes.guardar(new RemitenteDeCorreo("uno", "uno@khipu.pe", "r@khipu.pe"), AHORA, ADMIN);
        UUID otro = UUID.randomUUID();

        remitentes.guardar(new RemitenteDeCorreo(null, "dos@khipu.pe", null), AHORA.plusSeconds(60), otro);

        assertThat(filas("remitente_correo")).isEqualTo(1);
        var g = remitentes.buscar().orElseThrow();
        assertThat(g.remitente()).isEqualTo(new RemitenteDeCorreo(null, "dos@khipu.pe", null));
        assertThat(g.actualizadoEn()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(jdbc.queryForObject("SELECT actualizado_por FROM remitente_correo", UUID.class)).isEqualTo(otro);
    }

    @Test void quitarElRemitenteLoBorraYDiceSiHabia() {
        remitentes.guardar(new RemitenteDeCorreo(null, "avisos@khipu.pe", null), AHORA, ADMIN);

        assertThat(remitentes.quitar()).isTrue();
        assertThat(remitentes.buscar()).isEmpty();
        assertThat(remitentes.quitar()).isFalse();
    }

    @Test void laBaseNoAdmiteUnNombreOUnCorreoMasLargosQueLosDelDominio() {
        assertThatThrownBy(() -> remitentes.guardar(new RemitenteDeCorreo("n".repeat(101), "a@khipu.pe", null), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> remitentes.guardar(new RemitenteDeCorreo(null, "a".repeat(250) + "@khipu.pe", null), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        remitentes.guardar(new RemitenteDeCorreo("n".repeat(100), "a@khipu.pe", null), AHORA, ADMIN);
    }

    @Test void soloPuedeHaberUnaFilaDeRemitente() {
        remitentes.guardar(new RemitenteDeCorreo(null, "a@khipu.pe", null), AHORA, ADMIN);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO remitente_correo (id, email, actualizado_en) VALUES (2, 'b@khipu.pe', now())")).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- plantillas ---------------------------------------------------------------------------------------------------------------------

    @Test void sinPlantillasGuardadasNoHayNada() {
        assertThat(plantillas.todas()).isEmpty();
        assertThat(plantillas.buscar(PlantillaDeCorreo.BIENVENIDA)).isEmpty();
        assertThat(plantillas.quitar(PlantillaDeCorreo.BIENVENIDA)).isFalse();
    }

    @Test void unaPlantillaVuelveTalComoSeGuardo() {
        plantillas.guardar(PlantillaDeCorreo.AVISO_CREDENCIALES_SOL, new Texto("Aviso SOL", "Línea 1\nLínea 2 con tilde: ñ — {enlace}"), AHORA, ADMIN);

        var g = plantillas.buscar(PlantillaDeCorreo.AVISO_CREDENCIALES_SOL).orElseThrow();
        assertThat(g.texto()).isEqualTo(new Texto("Aviso SOL", "Línea 1\nLínea 2 con tilde: ñ — {enlace}"));
        assertThat(g.actualizadaEn()).isEqualTo(AHORA);
        assertThat(jdbc.queryForObject("SELECT actualizado_por FROM plantilla_correo", UUID.class)).isEqualTo(ADMIN);
    }

    @Test void guardarDeNuevoUnaPlantillaLaReemplazaSinDuplicarYNoTocaLasOtras() {
        plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Uno", "Entra a {enlace}"), AHORA, ADMIN);
        plantillas.guardar(PlantillaDeCorreo.RECUPERACION_CLAVE, new Texto("Otra", "Entra a {enlace}"), AHORA, ADMIN);

        plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Dos", "Ven a {enlace}"), AHORA.plusSeconds(60), ADMIN);

        assertThat(filas("plantilla_correo")).isEqualTo(2);
        assertThat(plantillas.buscar(PlantillaDeCorreo.BIENVENIDA).orElseThrow().texto()).isEqualTo(new Texto("Dos", "Ven a {enlace}"));
        assertThat(plantillas.buscar(PlantillaDeCorreo.BIENVENIDA).orElseThrow().actualizadaEn()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(plantillas.buscar(PlantillaDeCorreo.RECUPERACION_CLAVE).orElseThrow().texto().asunto()).isEqualTo("Otra");
    }

    @Test void todasTraeCadaPlantillaGuardadaPorSuTipo() {
        plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Uno", "Entra a {enlace}"), AHORA, ADMIN);
        plantillas.guardar(PlantillaDeCorreo.AVISO_CERTIFICADO_VENCIDO, new Texto("Dos", "Cuerpo"), AHORA, ADMIN);

        var todas = plantillas.todas();

        assertThat(todas).containsOnlyKeys(PlantillaDeCorreo.BIENVENIDA, PlantillaDeCorreo.AVISO_CERTIFICADO_VENCIDO);
        assertThat(todas.get(PlantillaDeCorreo.AVISO_CERTIFICADO_VENCIDO).texto()).isEqualTo(new Texto("Dos", "Cuerpo"));
    }

    @Test void unTipoQueEstaVersionYaNoConoceSeIgnoraEnLugarDeRomperLasDemas() {
        plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Uno", "Entra a {enlace}"), AHORA, ADMIN);
        jdbc.update("INSERT INTO plantilla_correo (tipo, asunto, cuerpo, actualizado_en) VALUES ('CORREO_QUE_YA_NO_EXISTE', 'x', 'y', now())");

        assertThat(plantillas.todas()).containsOnlyKeys(PlantillaDeCorreo.BIENVENIDA);
    }

    @Test void quitarUnaPlantillaLaBorraYDiceSiHabia() {
        plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Uno", "Entra a {enlace}"), AHORA, ADMIN);
        plantillas.guardar(PlantillaDeCorreo.RECUPERACION_CLAVE, new Texto("Otra", "Entra a {enlace}"), AHORA, ADMIN);

        assertThat(plantillas.quitar(PlantillaDeCorreo.BIENVENIDA)).isTrue();
        assertThat(plantillas.buscar(PlantillaDeCorreo.BIENVENIDA)).isEmpty();
        assertThat(plantillas.buscar(PlantillaDeCorreo.RECUPERACION_CLAVE)).isPresent();
        assertThat(plantillas.quitar(PlantillaDeCorreo.BIENVENIDA)).isFalse();
    }

    @Test void laBaseNoAdmiteUnAsuntoOUnCuerpoVaciosNiMasLargosQueLosDelDominio() {
        assertThatThrownBy(() -> plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("", "Entra a {enlace}"), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Hola", ""), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("a".repeat(PlantillaDeCorreo.MAX_ASUNTO + 1), "Entra a {enlace}"), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Hola", "a".repeat(PlantillaDeCorreo.MAX_CUERPO + 1)), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("a".repeat(PlantillaDeCorreo.MAX_ASUNTO), "a".repeat(PlantillaDeCorreo.MAX_CUERPO)), AHORA, ADMIN);
    }

    // --- banner -------------------------------------------------------------------------------------------------------------------------

    static final Instant DESDE = Instant.parse("2026-10-15T20:00:00.5Z");
    static final Instant HASTA = DESDE.plus(Duration.ofHours(4));

    @Test void sinBannerNoHayNada() {
        assertThat(banners.buscar()).isEmpty();
        assertThat(banners.retirar()).isFalse();
    }

    @Test void elBannerVuelveTalComoSeGuardoConSusInstantes() {
        banners.guardar(new BannerDeMantenimiento("Mantenimiento esta noche · 20:00", DESDE, HASTA), AHORA, ADMIN);

        var g = banners.buscar().orElseThrow();
        assertThat(g.banner()).isEqualTo(new BannerDeMantenimiento("Mantenimiento esta noche · 20:00", DESDE, HASTA));
        assertThat(g.actualizadoEn()).isEqualTo(AHORA);
        assertThat(jdbc.queryForObject("SELECT actualizado_por FROM banner_mantenimiento", UUID.class)).isEqualTo(ADMIN);
    }

    @Test void publicarOtroBannerReemplazaAlAnteriorSinDuplicar() {
        banners.guardar(new BannerDeMantenimiento("Uno", DESDE, HASTA), AHORA, ADMIN);

        banners.guardar(new BannerDeMantenimiento("Dos", DESDE.plusSeconds(60), HASTA.plusSeconds(60)), AHORA.plusSeconds(1), null);

        assertThat(filas("banner_mantenimiento")).isEqualTo(1);
        var g = banners.buscar().orElseThrow();
        assertThat(g.banner()).isEqualTo(new BannerDeMantenimiento("Dos", DESDE.plusSeconds(60), HASTA.plusSeconds(60)));
        assertThat(jdbc.queryForObject("SELECT actualizado_por FROM banner_mantenimiento", UUID.class)).isNull();
    }

    @Test void retirarElBannerLoBorraYDiceSiHabia() {
        banners.guardar(new BannerDeMantenimiento("Uno", DESDE, HASTA), AHORA, ADMIN);

        assertThat(banners.retirar()).isTrue();
        assertThat(banners.buscar()).isEmpty();
        assertThat(banners.retirar()).isFalse();
    }

    @Test void laBaseNoAdmiteUnBannerQueTermineAntesDeEmpezarNiUnTextoVacioONiMasLargo() {
        assertThatThrownBy(() -> banners.guardar(new BannerDeMantenimiento("x", HASTA, DESDE), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> banners.guardar(new BannerDeMantenimiento("x", DESDE, DESDE), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> banners.guardar(new BannerDeMantenimiento("", DESDE, HASTA), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> banners.guardar(new BannerDeMantenimiento("x".repeat(BannerDeMantenimiento.MAX_TEXTO + 1), DESDE, HASTA), AHORA, ADMIN)).isInstanceOf(DataIntegrityViolationException.class);
        banners.guardar(new BannerDeMantenimiento("x".repeat(BannerDeMantenimiento.MAX_TEXTO), DESDE, HASTA), AHORA, ADMIN);
    }

    // --- dentro de una transacción ------------------------------------------------------------------------------------------------------

    /** El cambio y su bitácora viajan juntos: si algo falla después de guardar, no queda ni el cambio. */
    @Test void unCambioDentroDeUnaTransaccionQueFallaNoQueda() {
        assertThatThrownBy(() -> uow.ejecutar(() -> {
            remitentes.guardar(new RemitenteDeCorreo(null, "a@khipu.pe", null), AHORA, ADMIN);
            plantillas.guardar(PlantillaDeCorreo.BIENVENIDA, new Texto("Uno", "Entra a {enlace}"), AHORA, ADMIN);
            banners.guardar(new BannerDeMantenimiento("Uno", DESDE, HASTA), AHORA, ADMIN);
            throw new IllegalStateException("la bitácora falló");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(filas("remitente_correo")).isZero();
        assertThat(filas("plantilla_correo")).isZero();
        assertThat(filas("banner_mantenimiento")).isZero();
    }
}
