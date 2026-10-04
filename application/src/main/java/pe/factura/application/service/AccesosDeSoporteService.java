package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AccesosDeSoporteUseCase;
import pe.factura.application.port.out.AccesosDeSoporteRepository;
import pe.factura.domain.plataforma.DetalleDeSoporte;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class AccesosDeSoporteService implements AccesosDeSoporteUseCase {
    private final AccesosDeSoporteRepository registros;

    @Override public List<AccesoDeSoporte> deLaCuenta(UUID cuentaId) {
        // Un registro que no se entiende no se esconde: el cliente tiene derecho a ver que hubo un acceso, aunque no sepamos decir más.
        return registros.deLaCuenta(cuentaId, MAXIMO).stream().map(r -> DetalleDeSoporte.leer(r.detalle())
                .map(d -> new AccesoDeSoporte(r.ocurridoEn(), d.usuario(), d.duracionSegundos()))
                .orElseGet(() -> new AccesoDeSoporte(r.ocurridoEn(), null, null))).toList();
    }
}
