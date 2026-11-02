package pe.factura.application.service;

import pe.factura.application.port.out.BannerRepository;
import pe.factura.application.port.out.PlantillasRepository;
import pe.factura.application.port.out.RemitenteRepository;
import pe.factura.domain.plataforma.BannerDeMantenimiento;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** La configuración de la plataforma (#199) en memoria. Registra quién cambió cada cosa y si el cambio corrió dentro de la transacción. */
final class ConfiguracionFake {
    private ConfiguracionFake() {}

    static final class Plantillas implements PlantillasRepository {
        final Map<PlantillaDeCorreo, Guardada> filas = new EnumMap<>(PlantillaDeCorreo.class);
        final Map<PlantillaDeCorreo, UUID> porQuien = new EnumMap<>(PlantillaDeCorreo.class);
        Fakes.UowTransaccional uow;
        boolean guardadoDentro;

        public Optional<Guardada> buscar(PlantillaDeCorreo tipo) { return Optional.ofNullable(filas.get(tipo)); }
        public Map<PlantillaDeCorreo, Guardada> todas() { return new EnumMap<>(filas); }
        public void guardar(PlantillaDeCorreo tipo, Texto texto, Instant ahora, UUID por) {
            guardadoDentro = uow != null && uow.dentro;
            filas.put(tipo, new Guardada(texto, ahora));
            porQuien.put(tipo, por);
        }
        public boolean quitar(PlantillaDeCorreo tipo) { return filas.remove(tipo) != null; }
    }

    static final class Remitente implements RemitenteRepository {
        Guardado fila;
        UUID porQuien;
        Fakes.UowTransaccional uow;
        boolean guardadoDentro;

        public Optional<Guardado> buscar() { return Optional.ofNullable(fila); }
        public void guardar(RemitenteDeCorreo remitente, Instant ahora, UUID por) {
            guardadoDentro = uow != null && uow.dentro;
            fila = new Guardado(remitente, ahora);
            porQuien = por;
        }
        public boolean quitar() { boolean habia = fila != null; fila = null; return habia; }
    }

    static final class Banner implements BannerRepository {
        Guardado fila;
        UUID porQuien;
        Fakes.UowTransaccional uow;
        boolean guardadoDentro;

        public Optional<Guardado> buscar() { return Optional.ofNullable(fila); }
        public void guardar(BannerDeMantenimiento banner, Instant ahora, UUID por) {
            guardadoDentro = uow != null && uow.dentro;
            fila = new Guardado(banner, ahora);
            porQuien = por;
        }
        public boolean retirar() { boolean habia = fila != null; fila = null; return habia; }
    }
}
