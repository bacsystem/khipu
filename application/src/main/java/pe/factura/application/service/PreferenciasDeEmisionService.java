package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.PreferenciasDeEmisionUseCase;
import pe.factura.application.port.out.PreferenciasDeEmisionRepository;
import pe.factura.domain.DomainException;

import java.util.UUID;

@RequiredArgsConstructor
public class PreferenciasDeEmisionService implements PreferenciasDeEmisionUseCase {
    private final PreferenciasDeEmisionRepository preferencias;

    @Override public PreferenciasDeEmision obtener(UUID tenantId) {
        return new PreferenciasDeEmision(preferencias.opcionesAvanzadas(tenantId));
    }

    @Override public PreferenciasDeEmision guardar(UUID tenantId, PreferenciasDeEmision nuevas) {
        if (!preferencias.fijarOpcionesAvanzadas(tenantId, nuevas.opcionesAvanzadas()))
            throw new DomainException("NO_ENCONTRADO", "Tenant no encontrado");
        return nuevas;
    }
}
