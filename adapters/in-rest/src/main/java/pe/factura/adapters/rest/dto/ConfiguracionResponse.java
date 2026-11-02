package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.BannerPublicado;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.PlantillaEditable;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.RemitenteVigente;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Instant;
import java.util.List;

/** Las respuestas de la configuración de la plataforma del backoffice (#199). */
public final class ConfiguracionResponse {
    private ConfiguracionResponse() {}

    /** De quién salen los correos. {@code nombre} y {@code responderA} están ausentes si no se fijaron. */
    public record Direccion(@Schema(example = "khipu") String nombre, @Schema(example = "no-responder@khipu.pe") String email, @Schema(example = "soporte@khipu.pe") String responderA) {
        static Direccion de(RemitenteDeCorreo r) { return new Direccion(r.nombre(), r.email(), r.responderA()); }
    }

    public record Remitente(
            @Schema(description = "El remitente con que salen los correos ahora") Direccion vigente,
            @Schema(description = "`true` si lo fijó un administrador; `false` si es el de la configuración del servidor") boolean personalizado,
            @Schema(example = "2026-10-15T15:00:00Z", description = "Cuándo se cambió; ausente si no es personalizado") Instant actualizadoEn,
            @Schema(example = "ana@khipu.pe", description = "Correo del administrador que lo fijó; ausente si no es personalizado, si fue la clave de la plataforma o si ya no existe")
            String actualizadoPor,
            @Schema(description = "El de la configuración del servidor (`MAIL_REMITENTE`), al que se vuelve al restablecer") Direccion predeterminado) {
        public static Remitente de(RemitenteVigente r) {
            return new Remitente(Direccion.de(r.vigente()), r.personalizado(), r.actualizadoEn(), r.actualizadoPor(), Direccion.de(r.predeterminado()));
        }
    }

    public record Variable(@Schema(example = "enlace") String nombre, @Schema(example = "El enlace de un solo uso.") String descripcion, @Schema(example = "https://app.khipu.pe/restablecer/0a1b2c3d") String ejemplo,
                           @Schema(description = "El correo no sirve sin ella: el cuerpo tiene que incluirla") boolean indispensable) {
        static Variable de(PlantillaDeCorreo.Variable v) { return new Variable(v.nombre(), v.descripcion(), v.ejemplo(), v.indispensable()); }
    }

    public record TextoDeCorreo(String asunto, String cuerpo) {
        public static TextoDeCorreo de(Texto t) { return new TextoDeCorreo(t.asunto(), t.cuerpo()); }
    }

    public record Plantilla(
            @Schema(example = "RECUPERACION_CLAVE") String tipo,
            @Schema(example = "Restablecer la contraseña") String etiqueta,
            @Schema(description = "Cuándo se manda este correo") String cuandoSeManda,
            @Schema(description = "El texto con que sale ahora") TextoDeCorreo vigente,
            @Schema(description = "El texto de fábrica, al que se vuelve al restaurar") TextoDeCorreo defecto,
            @Schema(description = "`true` si un administrador reemplazó el texto de fábrica") boolean personalizada,
            @Schema(example = "2026-10-15T15:00:00Z", description = "Cuándo se cambió; ausente si no es personalizada") Instant actualizadaEn,
            @Schema(example = "ana@khipu.pe", description = "Correo del administrador que la cambió; ausente si no es personalizada, si fue la clave de la plataforma o si ya no existe")
            String actualizadaPor,
            @Schema(description = "Las variables que el correo admite, escritas `{nombre}`") List<Variable> variables) {
        public static Plantilla de(PlantillaEditable p) {
            return new Plantilla(p.tipo().name(), p.tipo().etiqueta(), p.tipo().cuandoSeManda(), TextoDeCorreo.de(p.vigente()), TextoDeCorreo.de(p.defecto()), p.personalizada(), p.actualizadaEn(),
                    p.actualizadaPor(), p.tipo().variables().stream().map(Variable::de).toList());
        }
    }

    public record Banner(
            @Schema(example = "Mantenimiento programado esta noche de 22:00 a 23:00") String texto,
            @Schema(example = "2026-10-15T20:00:00Z") Instant desde,
            @Schema(example = "2026-10-16T01:00:00Z") Instant hasta,
            @Schema(example = "2026-10-15T15:00:00Z", description = "Cuándo se publicó o se cambió") Instant actualizadoEn,
            @Schema(description = "`true` si se está mostrando ahora; `false` si está programado para después o ya venció") boolean vigenteAhora) {
        public static Banner de(BannerPublicado b) { return new Banner(b.banner().texto(), b.banner().desde(), b.banner().hasta(), b.actualizadoEn(), b.vigenteAhora()); }
    }

    /** Lo que ven los clientes: solo el texto y su vigencia. */
    public record BannerPublico(@Schema(example = "Mantenimiento programado esta noche de 22:00 a 23:00") String texto, Instant desde, Instant hasta) {
        public static BannerPublico de(BannerDeMantenimiento b) { return new BannerPublico(b.texto(), b.desde(), b.hasta()); }
    }
}
