package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.ProductoRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ProductoGuardado;
import pe.factura.domain.documento.ProductoGuardado.Datos;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Productos guardados (#320): alta con código duplicado, cambio, páginas, y aislamiento por empresa. */
class GestionarProductosServiceTest {
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    static final UUID T = UUID.randomUUID(), OTRA = UUID.randomUUID();

    static class Repo implements ProductoRepository {
        final Map<UUID, ProductoGuardado> filas = new LinkedHashMap<>();
        private boolean hayOtroConEseCodigo(ProductoGuardado p) {
            return p.codigo() != null && filas.values().stream().anyMatch(x -> !x.id().equals(p.id()) && x.tenantId().equals(p.tenantId()) && p.codigo().equals(x.codigo()));
        }
        public boolean crear(ProductoGuardado p) { if (hayOtroConEseCodigo(p)) return false; filas.put(p.id(), p); return true; }
        public boolean actualizar(ProductoGuardado p) {
            if (!filas.containsKey(p.id()) || !filas.get(p.id()).tenantId().equals(p.tenantId()) || hayOtroConEseCodigo(p)) return false;
            filas.put(p.id(), p);
            return true;
        }
        public Optional<ProductoGuardado> buscar(UUID t, UUID id) { return Optional.ofNullable(filas.get(id)).filter(p -> p.tenantId().equals(t)); }
        List<ProductoGuardado> deLaEmpresa(UUID t, String texto) {
            return filas.values().stream().filter(p -> p.tenantId().equals(t) && (texto == null || p.descripcion().toLowerCase().contains(texto.toLowerCase()))).toList();
        }
        public List<ProductoGuardado> listar(UUID t, String texto, int pagina, int por) {
            List<ProductoGuardado> todos = new ArrayList<>(deLaEmpresa(t, texto));
            return todos.subList(Math.min((pagina - 1) * por, todos.size()), Math.min(pagina * por, todos.size()));
        }
        public long contar(UUID t, String texto) { return deLaEmpresa(t, texto).size(); }
        public boolean eliminar(UUID t, UUID id) { return buscar(t, id).isPresent() && filas.remove(id) != null; }
    }

    static class Directo implements UnitOfWork {
        public <T> T ejecutar(Supplier<T> t) { return t.get(); }
        public void ejecutar(Runnable t) { t.run(); }
    }

    Repo repo = new Repo();
    GestionarProductosService servicio = new GestionarProductosService(repo, new Directo(), Clock.fixed(AHORA, ZoneOffset.UTC));

    static Datos datos(String codigo, String descripcion) { return new Datos(codigo, descripcion, "ZZ", "10", new BigDecimal("118"), null); }

    @Test void crearGuardaYUnCodigoRepetidoEsDuplicadoSinEscribirNada() {
        ProductoGuardado p = servicio.crear(T, datos("SKU-1", "Servicio uno"));

        assertThat(repo.filas).containsKey(p.id());
        assertThatThrownBy(() -> servicio.crear(T, datos("SKU-1", "Otro"))).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(repo.filas).hasSize(1);
        assertThat(servicio.crear(OTRA, datos("SKU-1", "Servicio uno")).tenantId()).as("otra empresa puede repetir el código").isEqualTo(OTRA);
        assertThat(servicio.crear(T, datos(null, "Sin código")).codigo()).isNull();
        assertThat(servicio.crear(T, datos(null, "Sin código")).codigo()).as("sin código no hay duplicado").isNull();
    }

    @Test void unDatoInvalidoNoSeGuarda() {
        assertThatThrownBy(() -> servicio.crear(T, new Datos(null, "Servicio", "XYZ", "10", BigDecimal.TEN, null))).extracting("codigo").isEqualTo("ITEM_INVALIDO");
        assertThat(repo.filas).isEmpty();
    }

    @Test void obtenerYEliminarSoloDeLaPropiaEmpresa() {
        ProductoGuardado p = servicio.crear(T, datos("SKU-1", "Servicio uno"));

        assertThat(servicio.obtener(T, p.id())).isEqualTo(p);
        assertThatThrownBy(() -> servicio.obtener(OTRA, p.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> servicio.eliminar(OTRA, p.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        servicio.eliminar(T, p.id());
        assertThatThrownBy(() -> servicio.eliminar(T, p.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(repo.filas).isEmpty();
    }

    @Test void actualizarCambiaLosDatosPeroNoAUnCodigoQueYaEsDeOtro() {
        ProductoGuardado a = servicio.crear(T, datos("SKU-1", "Servicio uno"));
        servicio.crear(T, datos("SKU-2", "Servicio dos"));

        ProductoGuardado cambiado = servicio.actualizar(T, a.id(), new Datos("SKU-3", "Servicio uno plus", "HUR", "20", new BigDecimal("50"), null));
        assertThat(cambiado.codigo()).isEqualTo("SKU-3");
        assertThat(cambiado.unidad()).isEqualTo("HUR");
        assertThatThrownBy(() -> servicio.actualizar(T, a.id(), datos("SKU-2", "Servicio uno"))).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(repo.filas.get(a.id()).codigo()).isEqualTo("SKU-3");
        assertThatThrownBy(() -> servicio.actualizar(OTRA, a.id(), datos("SKU-9", "X producto"))).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    /** Un repositorio al que otra sesión le borra el producto justo antes de actualizarlo. */
    static class RepoConBajaConcurrente extends Repo {
        @Override public boolean actualizar(ProductoGuardado p) { filas.remove(p.id()); return super.actualizar(p); }
    }

    @Test void siElProductoSeBorraMientrasSeActualizaNoEsUnDuplicado() {
        GestionarProductosService s = new GestionarProductosService(new RepoConBajaConcurrente(), new Directo(), Clock.fixed(AHORA, ZoneOffset.UTC));
        ProductoGuardado p = s.crear(T, datos("SKU-1", "Servicio uno"));

        assertThatThrownBy(() -> s.actualizar(T, p.id(), datos("SKU-1", "Servicio uno plus"))).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void listarPaginaBuscaYValidaLaPaginacion() {
        for (int i = 0; i < 5; i++) servicio.crear(T, datos("S" + i, "Producto " + i));
        servicio.crear(OTRA, datos("X", "Producto ajeno"));

        var p1 = servicio.listar(T, "  ", 1, 2);
        var buscada = servicio.listar(T, " producto 3 ", 1, 20);

        assertThat(p1.total()).isEqualTo(5);
        assertThat(p1.productos()).hasSize(2);
        assertThat(servicio.listar(T, null, 3, 2).productos()).hasSize(1);
        assertThat(buscada.productos()).extracting(ProductoGuardado::descripcion).containsExactly("Producto 3");
        assertThatThrownBy(() -> servicio.listar(T, null, 0, 20)).extracting("codigo").isEqualTo("PAGINACION_INVALIDA");
        assertThatThrownBy(() -> servicio.listar(T, null, 1, 101)).extracting("codigo").isEqualTo("PAGINACION_INVALIDA");
    }
}
