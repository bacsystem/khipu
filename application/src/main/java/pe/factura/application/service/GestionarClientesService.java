package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.GestionarClientesUseCase;
import pe.factura.application.port.out.ClienteRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ClienteGuardado;
import pe.factura.domain.documento.ClienteGuardado.Datos;

import java.time.Clock;
import java.util.UUID;

@RequiredArgsConstructor
public class GestionarClientesService implements GestionarClientesUseCase {
    private final ClienteRepository clientes;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public ClienteGuardado crear(UUID tenantId, ClienteGuardado.Datos datos) {
        ClienteGuardado nuevo = ClienteGuardado.nuevo(tenantId, datos, clock.instant());
        if (!uow.ejecutar(() -> clientes.crear(nuevo))) throw duplicado(nuevo.tipoDoc(), nuevo.numDoc());
        return nuevo;
    }

    @Override public Guardado guardarPorDocumento(UUID tenantId, ClienteGuardado.Datos datos) {
        ClienteGuardado nuevo = ClienteGuardado.nuevo(tenantId, datos, clock.instant());
        // Crear primero y, si ya existía, actualizar: dos guardados a la vez del mismo cliente no se pisan (el segundo ve el duplicado y actualiza).
        return uow.ejecutar(() -> {
            if (clientes.crear(nuevo)) return new Guardado(nuevo, true);
            ClienteGuardado existente = clientes.buscarPorDocumento(tenantId, nuevo.tipoDoc(), nuevo.numDoc())
                    .orElseThrow(() -> new DomainException("ESTADO_CONFLICTO", "El cliente cambió mientras se guardaba; vuelva a intentar"));
            // Guardar al emitir manda lo que había en el comprobante, que no siempre trae el correo ni la dirección que la persona ya había guardado: lo que no llega se conserva.
            Datos mezclados = new Datos(datos.tipoDoc(), datos.numDoc(), datos.razonSocial(), datos.direccion() != null ? datos.direccion() : existente.direccion(),
                    datos.pais() != null ? datos.pais() : existente.pais(), datos.email() != null ? datos.email() : existente.email());
            ClienteGuardado actualizado = existente.con(mezclados, clock.instant());
            clientes.actualizar(actualizado);
            return new Guardado(actualizado, false);
        });
    }

    @Override public ClienteGuardado obtener(UUID tenantId, UUID id) {
        return clientes.buscar(tenantId, id).orElseThrow(GestionarClientesService::noEncontrado);
    }

    @Override public Pagina listar(UUID tenantId, String texto, int pagina, int porPagina) {
        if (pagina < 1 || porPagina < 1 || porPagina > 100) throw new DomainException("PAGINACION_INVALIDA", "pagina debe ser 1 o más y por_pagina de 1 a 100");
        String buscado = texto == null || texto.isBlank() ? null : texto.strip();
        return new Pagina(clientes.listar(tenantId, buscado, pagina, porPagina), clientes.contar(tenantId, buscado));
    }

    @Override public ClienteGuardado actualizar(UUID tenantId, UUID id, ClienteGuardado.Datos datos) {
        ClienteGuardado actualizado = obtener(tenantId, id).con(datos, clock.instant());
        if (!uow.ejecutar(() -> clientes.actualizar(actualizado))) throw duplicado(actualizado.tipoDoc(), actualizado.numDoc());
        return actualizado;
    }

    @Override public void eliminar(UUID tenantId, UUID id) {
        if (!uow.ejecutar(() -> clientes.eliminar(tenantId, id))) throw noEncontrado();
    }

    private static DomainException duplicado(String tipoDoc, String numDoc) {
        return new DomainException("DUPLICADO", "Ya tiene un cliente con el documento " + tipoDoc + " " + numDoc);
    }

    private static DomainException noEncontrado() { return new DomainException("NO_ENCONTRADO", "Cliente no encontrado"); }
}
