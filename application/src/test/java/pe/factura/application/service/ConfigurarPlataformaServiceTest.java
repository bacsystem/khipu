package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.BannerPublicado;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.PlantillaEditable;
import pe.factura.application.port.in.ConfigurarPlataformaUseCase.RemitenteVigente;
import pe.factura.application.port.out.BannerRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuración de la plataforma desde el backoffice (#199): lo que se rechaza, lo que se guarda y que cada cambio deje su rastro en la bitácora, en la misma transacción.
 */
class ConfigurarPlataformaServiceTest {
    static final UUID ADMIN = UUID.randomUUID();
    static final ActorAdmin ACTOR = ActorAdmin.administrador(ADMIN, "203.0.113.7");
    static final Instant AHORA = Fakes.CLOCK.instant();
    static final RemitenteDeCorreo PREDETERMINADO = new RemitenteDeCorreo(null, "no-responder@khipu.pe", null);

    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    ConfiguracionFake.Remitente remitentes = new ConfiguracionFake.Remitente();
    ConfiguracionFake.Plantillas plantillas = new ConfiguracionFake.Plantillas();
    ConfiguracionFake.Banner banners = new ConfiguracionFake.Banner();
    ConfigurarPlataformaService service;

    {
        auditoria.uow = uow;
        remitentes.uow = uow;
        plantillas.uow = uow;
        banners.uow = uow;
        service = new ConfigurarPlataformaService(remitentes, plantillas, banners, auditoria, uow, Fakes.CLOCK, PREDETERMINADO);
    }

    RegistroAuditoria unicoRegistro() {
        assertThat(auditoria.registros).hasSize(1);
        return auditoria.registros.get(0);
    }

    // --- remitente ----------------------------------------------------------------------------------------------------------------------

    @Test void sinRemitenteGuardadoSeVeElDeLaConfiguracion() {
        RemitenteVigente r = service.remitente();

        assertThat(r.vigente()).isEqualTo(PREDETERMINADO);
        assertThat(r.personalizado()).isFalse();
        assertThat(r.actualizadoEn()).isNull();
        assertThat(r.predeterminado()).isEqualTo(PREDETERMINADO);
    }

    @Test void cambiarElRemitenteLoGuardaYDiceQueEsPersonalizado() {
        RemitenteVigente r = service.cambiarRemitente(ACTOR, "  khipu ", "avisos@khipu.pe", "soporte@khipu.pe");

        assertThat(r.vigente()).isEqualTo(new RemitenteDeCorreo("khipu", "avisos@khipu.pe", "soporte@khipu.pe"));
        assertThat(r.personalizado()).isTrue();
        assertThat(r.actualizadoEn()).isEqualTo(AHORA);
        assertThat(r.predeterminado()).as("al restablecer se vuelve a este").isEqualTo(PREDETERMINADO);
        assertThat(remitentes.porQuien).isEqualTo(ADMIN);
        assertThat(service.remitente()).isEqualTo(r);
    }

    @Test void unRemitenteInvalidoNoSeGuardaNiDejaRegistro() {
        assertThatThrownBy(() -> service.cambiarRemitente(ACTOR, "khipu", "no es un correo", null)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("REMITENTE_INVALIDO");
        assertThatThrownBy(() -> service.cambiarRemitente(ACTOR, "khipu\nBcc: x@y.pe", "a@khipu.pe", null)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("REMITENTE_INVALIDO");

        assertThat(remitentes.fila).isNull();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void cambiarElRemitenteQuedaEnLaBitacoraEnLaMismaTransaccionConElAntesYElDespues() {
        service.cambiarRemitente(ACTOR, "khipu", "avisos@khipu.pe", "soporte@khipu.pe");

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.CAMBIAR_REMITENTE_DE_CORREO);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.cuentaId()).isNull();
        assertThat(r.tenantId()).isNull();
        assertThat(r.ocurridoEn()).isEqualTo(AHORA);
        assertThat(r.detalle()).isEqualTo("remitente=<no-responder@khipu.pe> -> khipu <avisos@khipu.pe> responder_a=soporte@khipu.pe");
        assertThat(remitentes.guardadoDentro).isTrue();
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void siLaBitacoraFallaElCambioSePropagaComoFallo() {
        auditoria.falla = new IllegalStateException("bitácora caída");

        assertThatThrownBy(() -> service.cambiarRemitente(ACTOR, "khipu", "avisos@khipu.pe", null)).isInstanceOf(IllegalStateException.class).hasMessage("bitácora caída");
    }

    @Test void elSegundoCambioDiceElRemitenteAnteriorGuardado() {
        service.cambiarRemitente(ACTOR, "khipu", "avisos@khipu.pe", null);
        auditoria.registros.clear();

        service.cambiarRemitente(ACTOR, null, "otro@khipu.pe", null);

        assertThat(unicoRegistro().detalle()).isEqualTo("remitente=khipu <avisos@khipu.pe> -> <otro@khipu.pe>");
    }

    @Test void restablecerElRemitenteVuelveAlDeLaConfiguracionYDejaRegistro() {
        service.cambiarRemitente(ACTOR, "khipu", "avisos@khipu.pe", null);
        auditoria.registros.clear();

        RemitenteVigente r = service.restablecerRemitente(ACTOR);

        assertThat(r.vigente()).isEqualTo(PREDETERMINADO);
        assertThat(r.personalizado()).isFalse();
        assertThat(remitentes.fila).isNull();
        assertThat(unicoRegistro().accion()).isEqualTo(AccionAdmin.CAMBIAR_REMITENTE_DE_CORREO);
        assertThat(unicoRegistro().detalle()).isEqualTo("remitente=khipu <avisos@khipu.pe> -> predeterminado <no-responder@khipu.pe>");
    }

    @Test void restablecerLoQueNoSeCambioNoHaceNadaNiDejaRegistro() {
        RemitenteVigente r = service.restablecerRemitente(ACTOR);

        assertThat(r.personalizado()).isFalse();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void conLaClaveDeLaPlataformaNoHayAdministradorQueLoCambio() {
        service.cambiarRemitente(ActorAdmin.clavePlataforma("203.0.113.7"), null, "avisos@khipu.pe", null);

        assertThat(remitentes.porQuien).isNull();
        assertThat(unicoRegistro().actor().administradorId()).isNull();
    }

    // --- plantillas ---------------------------------------------------------------------------------------------------------------------

    @Test void lasPlantillasSalenTodasEnElOrdenDeclaradoCadaUnaConSuTextoDeFabrica() {
        var lista = service.plantillas();

        assertThat(lista).extracting(PlantillaEditable::tipo).containsExactly(PlantillaDeCorreo.values());
        assertThat(lista).allSatisfy(p -> {
            assertThat(p.vigente()).isEqualTo(p.tipo().defecto());
            assertThat(p.defecto()).isEqualTo(p.tipo().defecto());
            assertThat(p.personalizada()).isFalse();
            assertThat(p.actualizadaEn()).isNull();
        });
    }

    @Test void guardarUnaPlantillaLaDejaComoElTextoVigenteDeEseCorreoSolamente() {
        PlantillaEditable p = service.guardarPlantilla(ACTOR, PlantillaDeCorreo.RECUPERACION_CLAVE, "  Tu enlace  ", "Entra a {enlace}\r\n");

        assertThat(p.vigente()).isEqualTo(new Texto("Tu enlace", "Entra a {enlace}"));
        assertThat(p.defecto()).isEqualTo(PlantillaDeCorreo.RECUPERACION_CLAVE.defecto());
        assertThat(p.personalizada()).isTrue();
        assertThat(p.actualizadaEn()).isEqualTo(AHORA);
        assertThat(plantillas.porQuien.get(PlantillaDeCorreo.RECUPERACION_CLAVE)).isEqualTo(ADMIN);
        assertThat(service.plantillas()).filteredOn(PlantillaEditable::personalizada).extracting(PlantillaEditable::tipo).containsExactly(PlantillaDeCorreo.RECUPERACION_CLAVE);
    }

    @Test void unaPlantillaInvalidaNoSeGuardaNiDejaRegistro() {
        assertThatThrownBy(() -> service.guardarPlantilla(ACTOR, PlantillaDeCorreo.RECUPERACION_CLAVE, "Hola", "Sin el enlace"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PLANTILLA_INVALIDA");
        assertThatThrownBy(() -> service.guardarPlantilla(ACTOR, PlantillaDeCorreo.RECUPERACION_CLAVE, "Hola\nBcc: x@y.pe", "Entra a {enlace}"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PLANTILLA_INVALIDA");

        assertThat(plantillas.filas).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void guardarUnaPlantillaQuedaEnLaBitacoraEnLaMismaTransaccionSinElTexto() {
        service.guardarPlantilla(ACTOR, PlantillaDeCorreo.AVISO_CREDENCIALES_SOL, "Aviso SOL", "Texto secreto del cuerpo");

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.EDITAR_PLANTILLA_DE_CORREO);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.detalle()).isEqualTo("plantilla=AVISO_CREDENCIALES_SOL").doesNotContain("secreto");
        assertThat(plantillas.guardadoDentro).isTrue();
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void restaurarUnaPlantillaVuelveAlTextoDeFabricaYDejaRegistro() {
        service.guardarPlantilla(ACTOR, PlantillaDeCorreo.AVISO_CREDENCIALES_SOL, "Aviso SOL", "Texto");
        auditoria.registros.clear();

        PlantillaEditable p = service.restaurarPlantilla(ACTOR, PlantillaDeCorreo.AVISO_CREDENCIALES_SOL);

        assertThat(p.vigente()).isEqualTo(PlantillaDeCorreo.AVISO_CREDENCIALES_SOL.defecto());
        assertThat(p.personalizada()).isFalse();
        assertThat(plantillas.filas).isEmpty();
        assertThat(unicoRegistro().accion()).isEqualTo(AccionAdmin.RESTAURAR_PLANTILLA_DE_CORREO);
        assertThat(unicoRegistro().detalle()).isEqualTo("plantilla=AVISO_CREDENCIALES_SOL");
    }

    @Test void restaurarLoQueNoSeCambioNoHaceNadaNiDejaRegistro() {
        PlantillaEditable p = service.restaurarPlantilla(ACTOR, PlantillaDeCorreo.BIENVENIDA);

        assertThat(p.personalizada()).isFalse();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void restaurarUnaNoTocaLasOtras() {
        service.guardarPlantilla(ACTOR, PlantillaDeCorreo.AVISO_CREDENCIALES_SOL, "Aviso SOL", "Texto");
        service.guardarPlantilla(ACTOR, PlantillaDeCorreo.BIENVENIDA, "Hola", "Entra a {enlace}");

        service.restaurarPlantilla(ACTOR, PlantillaDeCorreo.AVISO_CREDENCIALES_SOL);

        assertThat(plantillas.filas).containsOnlyKeys(PlantillaDeCorreo.BIENVENIDA);
    }

    @Test void laVistaPreviaValidaYUsaLosEjemplosSinGuardarNiDejarRegistro() {
        Texto t = service.vistaPrevia(PlantillaDeCorreo.RECUPERACION_CLAVE, "Tu enlace", "Entra a {enlace} (válido {validez})");

        assertThat(t).isEqualTo(new Texto("Tu enlace", "Entra a https://app.khipu.pe/restablecer/0a1b2c3d (válido 1 hora)"));
        assertThat(plantillas.filas).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void laVistaPreviaRechazaLoMismoQueGuardar() {
        assertThatThrownBy(() -> service.vistaPrevia(PlantillaDeCorreo.RECUPERACION_CLAVE, "Hola", "Sin enlace"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PLANTILLA_INVALIDA");
        assertThatThrownBy(() -> service.vistaPrevia(PlantillaDeCorreo.RECUPERACION_CLAVE, "Hola {nada}", "Entra a {enlace}"))
                .isInstanceOf(DomainException.class).hasMessageContaining("{nada}");
    }

    // --- banner -------------------------------------------------------------------------------------------------------------------------

    static final Instant DESDE = AHORA.plus(Duration.ofHours(1));
    static final Instant HASTA = AHORA.plus(Duration.ofHours(5));

    @Test void sinBannerNoHayNada() {
        assertThat(service.banner()).isEmpty();
    }

    @Test void publicarUnBannerLoGuardaConSuVigencia() {
        BannerPublicado b = service.publicarBanner(ACTOR, "  Mantenimiento esta noche ", DESDE, HASTA);

        assertThat(b.banner()).isEqualTo(new BannerDeMantenimiento("Mantenimiento esta noche", DESDE, HASTA));
        assertThat(b.actualizadoEn()).isEqualTo(AHORA);
        assertThat(banners.porQuien).isEqualTo(ADMIN);
        assertThat(service.banner()).contains(b);
    }

    @Test void unBannerProgramadoParaDespuesNoSeEstaMostrandoAun() {
        assertThat(service.publicarBanner(ACTOR, "Mantenimiento", DESDE, HASTA).vigenteAhora()).isFalse();
    }

    @Test void unBannerQueYaEmpezoSeEstaMostrando() {
        BannerPublicado b = service.publicarBanner(ACTOR, "Mantenimiento", AHORA.minus(Duration.ofHours(1)), HASTA);

        assertThat(b.vigenteAhora()).isTrue();
        assertThat(service.banner().orElseThrow().vigenteAhora()).isTrue();
    }

    @Test void unBannerVencidoSeguiaGuardadoPeroYaNoSeMuestra() {
        banners.fila = new BannerRepository.Guardado(new BannerDeMantenimiento("Viejo", AHORA.minus(Duration.ofDays(2)), AHORA.minus(Duration.ofDays(1))), AHORA.minus(Duration.ofDays(3)));

        BannerPublicado b = service.banner().orElseThrow();

        assertThat(b.banner().texto()).isEqualTo("Viejo");
        assertThat(b.vigenteAhora()).isFalse();
    }

    @Test void unBannerInvalidoNoSeGuardaNiDejaRegistro() {
        assertThatThrownBy(() -> service.publicarBanner(ACTOR, "  ", DESDE, HASTA)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("BANNER_INVALIDO");
        assertThatThrownBy(() -> service.publicarBanner(ACTOR, "x", HASTA, DESDE)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("BANNER_INVALIDO");
        // Ya venció: empezó hace horas y terminó hace un segundo (el orden de las fechas está bien, lo que falla es que el fin ya pasó).
        assertThatThrownBy(() -> service.publicarBanner(ACTOR, "x", AHORA.minus(Duration.ofHours(3)), AHORA.minusSeconds(1))).isInstanceOf(DomainException.class).hasMessageContaining("ya venció")
                .extracting("codigo").isEqualTo("BANNER_INVALIDO");

        assertThat(banners.fila).isNull();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void publicarOtroBannerReemplazaAlAnterior() {
        service.publicarBanner(ACTOR, "Uno", DESDE, HASTA);

        service.publicarBanner(ACTOR, "Dos", DESDE, HASTA.plusSeconds(60));

        assertThat(service.banner().orElseThrow().banner().texto()).isEqualTo("Dos");
    }

    @Test void publicarUnBannerQuedaEnLaBitacoraEnLaMismaTransaccionConSuTextoYVigencia() {
        service.publicarBanner(ACTOR, "Mantenimiento esta noche", DESDE, HASTA);

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.PUBLICAR_BANNER);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.detalle()).isEqualTo("texto=Mantenimiento esta noche desde=" + DESDE + " hasta=" + HASTA);
        assertThat(banners.guardadoDentro).isTrue();
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    /** El antes y el después de un remitente largo no caben en los 500 caracteres de la bitácora: se recorta en lugar de hacer fallar el cambio. */
    @Test void elDetalleDeLaBitacoraNuncaPasaDeLoQueCabe() {
        String largo = "a".repeat(64) + "@" + "b".repeat(100) + ".pe";
        service.cambiarRemitente(ACTOR, "n".repeat(100), largo, largo);
        auditoria.registros.clear();

        service.cambiarRemitente(ACTOR, "m".repeat(100), largo, largo);

        assertThat(unicoRegistro().detalle()).hasSize(500).startsWith("remitente=" + "n".repeat(100));
    }

    @Test void unDetalleCortoSeGuardaEntero() {
        service.publicarBanner(ACTOR, "Mantenimiento", DESDE, HASTA);

        assertThat(unicoRegistro().detalle()).isEqualTo("texto=Mantenimiento desde=" + DESDE + " hasta=" + HASTA);
    }

    @Test void retirarElBannerLoQuitaYDejaRegistro() {
        service.publicarBanner(ACTOR, "Mantenimiento", DESDE, HASTA);
        auditoria.registros.clear();

        service.retirarBanner(ACTOR);

        assertThat(banners.fila).isNull();
        assertThat(service.banner()).isEmpty();
        assertThat(unicoRegistro().accion()).isEqualTo(AccionAdmin.RETIRAR_BANNER);
        assertThat(unicoRegistro().detalle()).isEqualTo("texto=Mantenimiento");
    }

    @Test void retirarSinBannerEsUnoQueNoExisteYNoDejaRegistro() {
        assertThatThrownBy(() -> service.retirarBanner(ACTOR)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");

        assertThat(auditoria.registros).isEmpty();
    }

    /** Dos administradores retirando a la vez: el segundo ve que ya no hay y lo dice, en lugar de dejar un registro de algo que no hizo. */
    @Test void siElBannerSeRetiroEntreLaLecturaYElBorradoNoQuedaRegistro() {
        service.publicarBanner(ACTOR, "Mantenimiento", DESDE, HASTA);
        auditoria.registros.clear();
        BannerRepository otroAdministradorSeAdelanta = new BannerRepository() {
            public java.util.Optional<Guardado> buscar() { return banners.buscar(); }
            public void guardar(BannerDeMantenimiento banner, Instant ahora, UUID por) { throw new AssertionError(); }
            public boolean retirar() { banners.retirar(); return false; }
        };
        var service = new ConfigurarPlataformaService(remitentes, plantillas, otroAdministradorSeAdelanta, auditoria, uow, Fakes.CLOCK, PREDETERMINADO);

        assertThatThrownBy(() -> service.retirarBanner(ACTOR)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(auditoria.registros).isEmpty();
    }
}
