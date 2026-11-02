package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.BannerRepository;
import pe.factura.application.port.out.PlantillasRepository;
import pe.factura.application.port.out.RemitenteRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * La configuración de la plataforma (#199). Cada cambio se valida en el dominio y se guarda **en la misma transacción que su registro en la bitácora**: si la bitácora no puede
 * escribir, el cambio no queda. La bitácora dice qué se cambió, no los textos de los correos (esos viven en la plantilla); el remitente y el banner son datos de la plataforma, no de
 * un cliente, así que sí van en el detalle. Restablecer algo que no se había cambiado no hace nada ni deja registro.
 */
@RequiredArgsConstructor
public class ConfigurarPlataformaService implements ConfigurarPlataformaUseCase {
    /** La bitácora guarda hasta 500 caracteres de detalle. */
    private static final int MAX_DETALLE = 500;

    private final RemitenteRepository remitentes;
    private final PlantillasRepository plantillas;
    private final BannerRepository banners;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;
    /** El remitente de la configuración del servidor: el de siempre mientras nadie fije otro. */
    private final RemitenteDeCorreo predeterminado;

    // --- remitente ----------------------------------------------------------------------------------------------------------------------

    @Override public RemitenteVigente remitente() {
        return remitentes.buscar().map(g -> new RemitenteVigente(g.remitente(), true, g.actualizadoEn(), g.actualizadoPor(), predeterminado)).orElseGet(() -> new RemitenteVigente(predeterminado, false, null, predeterminado));
    }

    @Override public RemitenteVigente cambiarRemitente(ActorAdmin actor, String nombre, String email, String responderA) {
        RemitenteDeCorreo nuevo = RemitenteDeCorreo.de(nombre, email, responderA);
        Instant ahora = clock.instant();
        RemitenteDeCorreo antes = remitente().vigente();
        uow.ejecutar(() -> {
            remitentes.guardar(nuevo, ahora, actor.administradorId());
            registrar(actor, AccionAdmin.CAMBIAR_REMITENTE_DE_CORREO, "remitente=" + describir(antes) + " -> " + describir(nuevo), ahora);
        });
        return remitente();
    }

    @Override public RemitenteVigente restablecerRemitente(ActorAdmin actor) {
        Instant ahora = clock.instant();
        RemitenteDeCorreo antes = remitente().vigente();
        uow.ejecutar(() -> {
            if (remitentes.quitar()) registrar(actor, AccionAdmin.CAMBIAR_REMITENTE_DE_CORREO, "remitente=" + describir(antes) + " -> predeterminado " + describir(predeterminado), ahora);
        });
        return remitente();
    }

    // --- plantillas ---------------------------------------------------------------------------------------------------------------------

    @Override public List<PlantillaEditable> plantillas() {
        var guardadas = plantillas.todas();
        return java.util.Arrays.stream(PlantillaDeCorreo.values()).map(t -> editable(t, Optional.ofNullable(guardadas.get(t)))).toList();
    }

    @Override public PlantillaEditable guardarPlantilla(ActorAdmin actor, PlantillaDeCorreo tipo, String asunto, String cuerpo) {
        Texto texto = tipo.validar(asunto, cuerpo);
        Instant ahora = clock.instant();
        uow.ejecutar(() -> {
            plantillas.guardar(tipo, texto, ahora, actor.administradorId());
            registrar(actor, AccionAdmin.EDITAR_PLANTILLA_DE_CORREO, "plantilla=" + tipo.name(), ahora);
        });
        return editable(tipo, plantillas.buscar(tipo));
    }

    @Override public PlantillaEditable restaurarPlantilla(ActorAdmin actor, PlantillaDeCorreo tipo) {
        Instant ahora = clock.instant();
        uow.ejecutar(() -> {
            if (plantillas.quitar(tipo)) registrar(actor, AccionAdmin.RESTAURAR_PLANTILLA_DE_CORREO, "plantilla=" + tipo.name(), ahora);
        });
        return editable(tipo, plantillas.buscar(tipo));
    }

    @Override public Texto vistaPrevia(PlantillaDeCorreo tipo, String asunto, String cuerpo) {
        return tipo.vistaPrevia(tipo.validar(asunto, cuerpo));
    }

    private static PlantillaEditable editable(PlantillaDeCorreo tipo, Optional<PlantillasRepository.Guardada> guardada) {
        return new PlantillaEditable(tipo, guardada.map(PlantillasRepository.Guardada::texto).orElse(tipo.defecto()), tipo.defecto(), guardada.isPresent(),
                guardada.map(PlantillasRepository.Guardada::actualizadaEn).orElse(null), guardada.map(PlantillasRepository.Guardada::actualizadaPor).orElse(null));
    }

    // --- banner -------------------------------------------------------------------------------------------------------------------------

    @Override public Optional<BannerPublicado> banner() {
        Instant ahora = clock.instant();
        return banners.buscar().map(g -> new BannerPublicado(g.banner(), g.actualizadoEn(), g.banner().vigenteEn(ahora)));
    }

    @Override public BannerPublicado publicarBanner(ActorAdmin actor, String texto, Instant desde, Instant hasta) {
        Instant ahora = clock.instant();
        BannerDeMantenimiento banner = BannerDeMantenimiento.de(texto, desde, hasta, ahora);
        uow.ejecutar(() -> {
            banners.guardar(banner, ahora, actor.administradorId());
            registrar(actor, AccionAdmin.PUBLICAR_BANNER, "texto=" + banner.texto() + " desde=" + banner.desde() + " hasta=" + banner.hasta(), ahora);
        });
        return new BannerPublicado(banner, ahora, banner.vigenteEn(ahora));
    }

    @Override public void retirarBanner(ActorAdmin actor) {
        Instant ahora = clock.instant();
        String texto = banners.buscar().map(g -> g.banner().texto()).orElse("");
        uow.ejecutar(() -> {
            // Quien decide si había algo que retirar es el borrado: dos administradores a la vez, y el segundo lo ve.
            if (!banners.retirar()) throw new DomainException("NO_ENCONTRADO", "No hay un aviso publicado");
            registrar(actor, AccionAdmin.RETIRAR_BANNER, "texto=" + texto, ahora);
        });
    }

    // --- bitácora -----------------------------------------------------------------------------------------------------------------------

    private void registrar(ActorAdmin actor, AccionAdmin accion, String detalle, Instant ahora) {
        auditoria.registrar(RegistroAuditoria.de(actor, accion, null, null, detalle.length() > MAX_DETALLE ? detalle.substring(0, MAX_DETALLE) : detalle, ahora));
    }

    private static String describir(RemitenteDeCorreo r) {
        return (r.nombre() == null ? "" : r.nombre() + " ") + "<" + r.email() + ">" + (r.responderA() == null ? "" : " responder_a=" + r.responderA());
    }
}
