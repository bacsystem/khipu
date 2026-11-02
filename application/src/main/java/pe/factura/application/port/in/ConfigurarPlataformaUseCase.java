package pe.factura.application.port.in;

import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * La configuración de la plataforma que un administrador cambia sin un despliegue (#199): el remitente de los correos, el texto de cada correo y el aviso de mantenimiento. Todo
 * cambio queda en la bitácora a nombre de {@code actor}, en la misma transacción que el cambio. Lo que se rechaza lo dicen {@code REMITENTE_INVALIDO}, {@code PLANTILLA_INVALIDA} y
 * {@code BANNER_INVALIDO}, con un mensaje que explica qué corregir.
 */
public interface ConfigurarPlataformaUseCase {
    /**
     * El remitente con que salen los correos ahora. {@code personalizado} dice si lo fijó un administrador o es el de la configuración del servidor ({@code predeterminado}, al que
     * se vuelve al restablecer); {@code actualizadoEn} es nulo cuando no es personalizado, y {@code actualizadoPor} (H11) es el correo de quien lo fijó.
     */
    record RemitenteVigente(RemitenteDeCorreo vigente, boolean personalizado, Instant actualizadoEn, String actualizadoPor, RemitenteDeCorreo predeterminado) {
        public RemitenteVigente(RemitenteDeCorreo vigente, boolean personalizado, Instant actualizadoEn, RemitenteDeCorreo predeterminado) {
            this(vigente, personalizado, actualizadoEn, null, predeterminado);
        }
    }

    /** Un correo editable: el texto con que sale ahora, el de fábrica, y si alguien lo cambió (y cuándo). */
    record PlantillaEditable(PlantillaDeCorreo tipo, Texto vigente, Texto defecto, boolean personalizada, Instant actualizadaEn, String actualizadaPor) {
        public PlantillaEditable(PlantillaDeCorreo tipo, Texto vigente, Texto defecto, boolean personalizada, Instant actualizadaEn) {
            this(tipo, vigente, defecto, personalizada, actualizadaEn, null);
        }
    }

    /** El aviso de mantenimiento publicado y si se está mostrando ahora (puede estar programado para después, o haber vencido). */
    record BannerPublicado(BannerDeMantenimiento banner, Instant actualizadoEn, boolean vigenteAhora) {}

    RemitenteVigente remitente();

    RemitenteVigente cambiarRemitente(ActorAdmin actor, String nombre, String email, String responderA);

    /** Vuelve al remitente de la configuración del servidor. Sin remitente propio no hace nada ni deja registro. */
    RemitenteVigente restablecerRemitente(ActorAdmin actor);

    /** Todos los correos, en el orden en que se declaran. */
    List<PlantillaEditable> plantillas();

    PlantillaEditable guardarPlantilla(ActorAdmin actor, PlantillaDeCorreo tipo, String asunto, String cuerpo);

    /** Vuelve el correo a su texto de fábrica. Sin texto propio no hace nada ni deja registro. */
    PlantillaEditable restaurarPlantilla(ActorAdmin actor, PlantillaDeCorreo tipo);

    /** Cómo se vería ese texto con valores de ejemplo, validándolo como al guardar. No guarda nada ni deja registro. */
    Texto vistaPrevia(PlantillaDeCorreo tipo, String asunto, String cuerpo);

    Optional<BannerPublicado> banner();

    BannerPublicado publicarBanner(ActorAdmin actor, String texto, Instant desde, Instant hasta);

    /** {@code NO_ENCONTRADO} si no hay un aviso publicado. */
    void retirarBanner(ActorAdmin actor);
}
