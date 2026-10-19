package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.GestionarProductosUseCase;
import pe.factura.application.port.out.ProductoRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ProductoGuardado;

import java.time.Clock;
import java.util.UUID;

@RequiredArgsConstructor
public class GestionarProductosService implements GestionarProductosUseCase {
    private final ProductoRepository productos;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public ProductoGuardado crear(UUID tenantId, ProductoGuardado.Datos datos) {
        ProductoGuardado nuevo = ProductoGuardado.nuevo(tenantId, datos, clock.instant());
        if (!uow.ejecutar(() -> productos.crear(nuevo))) throw duplicado(nuevo.codigo());
        return nuevo;
    }

    @Override public ProductoGuardado obtener(UUID tenantId, UUID id) {
        return productos.buscar(tenantId, id).orElseThrow(GestionarProductosService::noEncontrado);
    }

    @Override public Pagina listar(UUID tenantId, String texto, int pagina, int porPagina) {
        if (pagina < 1 || porPagina < 1 || porPagina > 100) throw new DomainException("PAGINACION_INVALIDA", "pagina debe ser 1 o más y por_pagina de 1 a 100");
        String buscado = texto == null || texto.isBlank() ? null : texto.strip();
        return new Pagina(productos.listar(tenantId, buscado, pagina, porPagina), productos.contar(tenantId, buscado));
    }

    @Override public ProductoGuardado actualizar(UUID tenantId, UUID id, ProductoGuardado.Datos datos) {
        ProductoGuardado actualizado = obtener(tenantId, id).con(datos, clock.instant());
        if (!uow.ejecutar(() -> productos.actualizar(actualizado))) {
            // `false` es «el código ya es de otro» o «la fila ya no está» (otra sesión lo borró entre leerlo y guardarlo): cada uno tiene su respuesta.
            if (productos.buscar(tenantId, id).isEmpty()) throw noEncontrado();
            throw duplicado(actualizado.codigo());
        }
        return actualizado;
    }

    @Override public void eliminar(UUID tenantId, UUID id) {
        if (!uow.ejecutar(() -> productos.eliminar(tenantId, id))) throw noEncontrado();
    }

    private static DomainException duplicado(String codigo) { return new DomainException("DUPLICADO", "Ya tiene un producto con el código " + codigo); }

    private static DomainException noEncontrado() { return new DomainException("NO_ENCONTRADO", "Producto no encontrado"); }
}
