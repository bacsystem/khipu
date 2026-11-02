package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AccesosDeSoporteUseCase;
import pe.factura.application.port.in.AccesosDeSoporteUseCase.AccesoDeSoporte;
import pe.factura.application.port.out.AccesosDeSoporteRepository;
import pe.factura.application.port.out.AccesosDeSoporteRepository.Registro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El historial de accesos de soporte que ve el cliente (#184). */
class AccesosDeSoporteServiceTest {
    static final Instant T = Instant.parse("2026-10-04T10:00:00Z");

    UUID cuentaId = UUID.randomUUID();
    List<Registro> registros = new ArrayList<>();
    UUID pedidaCuenta;
    int pedidoLimite;

    AccesosDeSoporteRepository repo = (cuenta, limite) -> {
        pedidaCuenta = cuenta;
        pedidoLimite = limite;
        return registros;
    };
    AccesosDeSoporteService service = new AccesosDeSoporteService(repo);

    @Test void cadaRegistroDiceCuandoAQuienYPorCuantoTiempo() {
        registros.add(new Registro(T, "usuario=ana@negocio.pe duracion_s=900"));

        List<AccesoDeSoporte> r = service.deLaCuenta(cuentaId);

        assertThat(r).containsExactly(new AccesoDeSoporte(T, "ana@negocio.pe", 900L));
    }

    @Test void conservaElOrdenQueLeDaElRepositorio() {
        registros.add(new Registro(T.plusSeconds(60), "usuario=b@x.pe duracion_s=900"));
        registros.add(new Registro(T, "usuario=a@x.pe duracion_s=900"));

        assertThat(service.deLaCuenta(cuentaId)).extracting(AccesoDeSoporte::usuario).containsExactly("b@x.pe", "a@x.pe");
    }

    /** El cliente tiene derecho a ver que hubo un acceso aunque el registro no tenga el formato que se entiende: no se esconde. */
    @Test void unRegistroQueNoSeEntiendeSeMuestraSoloConLaFecha() {
        registros.add(new Registro(T, "algo que una versión futura escribió"));
        registros.add(new Registro(T.plusSeconds(5), null));

        assertThat(service.deLaCuenta(cuentaId)).containsExactly(new AccesoDeSoporte(T, null, null), new AccesoDeSoporte(T.plusSeconds(5), null, null));
    }

    @Test void pideSoloLosDeLaCuentaConElMaximo() {
        service.deLaCuenta(cuentaId);

        assertThat(pedidaCuenta).isEqualTo(cuentaId);
        assertThat(pedidoLimite).isEqualTo(AccesosDeSoporteUseCase.MAXIMO);
    }

    @Test void sinAccesosDevuelveUnaListaVacia() {
        assertThat(service.deLaCuenta(cuentaId)).isEmpty();
    }

    /** El cliente no sabe qué administrador fue: nada de la identidad del administrador llega al historial. */
    @Test void elHistorialNoTieneNingunCampoDelAdministrador() {
        assertThat(AccesoDeSoporte.class.getRecordComponents()).extracting(c -> c.getName()).containsExactly("ocurridoEn", "usuario", "duracionSegundos");
    }
}
